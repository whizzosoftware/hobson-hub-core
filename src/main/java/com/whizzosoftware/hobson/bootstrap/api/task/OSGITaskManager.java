/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonInvalidRequestException;
import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.TaskExecutionEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.task.*;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.task.actionset.ActionSetStore;
import com.whizzosoftware.hobson.bootstrap.api.task.actionset.MapDBActionSetStore;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * An OSGi implementation of TaskManager.
 *
 * @author Dan Noguerol
 */
public class OSGITaskManager implements TaskManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile ExecutorService executorService;
    private volatile HubManager hubManager;
    private volatile PluginManager pluginManager;
    private volatile VariableManager variableManager;

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();
    private ActionSetStore actionSetStore = null;

    public void start() {
        PluginContext ctx = PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName());

        // create action set store
        actionSetStore = new MapDBActionSetStore(pluginManager.getDataFile(ctx, "actionSets"), this);
    }

    @Override
    public void publishTask(HobsonTask task) {
        String pluginId = task.getContext().getPluginId();
        BundleContext context = BundleUtil.getBundleContext(getClass(), pluginId);

        // check that the task doesn't already exist
        if (getTask(task.getContext(), false) != null) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate task: " + task.getContext());
        }

        if (context != null) {
            // register task as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            props.put("taskId", task.getContext().getTaskId());

            synchronized (serviceRegistrationMap) {
                List<ServiceRegistration> regList = serviceRegistrationMap.get(pluginId);
                if (regList == null) {
                    regList = new ArrayList<>();
                    serviceRegistrationMap.put(pluginId, regList);
                }
                regList.add(
                    context.registerService(
                            HobsonTask.class.getName(),
                            task,
                            props
                    )
                );
            }

            logger.debug("Task {} registered", task.getContext());
        }
    }

    @Override
    public void unpublishAllActionClasses(PluginContext ctx) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    if (sr.getReference().getProperty("actionClassId") != null) {
                        sr.unregister();
                    }
                }
                serviceRegistrationMap.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public void unpublishAllActionSets(PluginContext ctx) {
    }

    @Override
    public void unpublishAllConditionClasses(PluginContext ctx) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    if (sr.getReference().getProperty("conditionClassId") != null) {
                        sr.unregister();
                    }
                }
                serviceRegistrationMap.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public void unpublishAllTasks(PluginContext ctx) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    if (sr.getReference().getProperty("taskId") != null) {
                        sr.unregister();
                    }
                }
                serviceRegistrationMap.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public void unpublishTask(TaskContext ctx) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    if (sr.getReference().getProperty("taskId").equals(ctx.getTaskId())) {
                        sr.unregister();
                        return;
                    }
                }
            }
            throw new HobsonNotFoundException("Unable to find task: " + ctx);
        }
    }

    @Override
    public Collection<HobsonTask> getAllTasks(HubContext ctx) {
        List<HobsonTask> tasks = new ArrayList<>();
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(HobsonTask.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    tasks.add((HobsonTask) bundleContext.getService(ref));
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
        return tasks;
    }

    @Override
    public PropertyContainerClass getConditionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(conditionClassId=" + ctx.getContainerClassId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (PropertyContainerClass)bundleContext.getService(refs[0]);
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error executing action: " + ctx, e);
        }
    }

    @Override
    public HobsonTask getTask(TaskContext ctx) {
        return getTask(ctx, true);
    }

    protected HobsonTask getTask(TaskContext ctx, boolean failOnError) {
        HobsonTask result = null;

        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + HobsonTask.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(taskId=" + ctx.getTaskId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                result = (HobsonTask)bundleContext.getService(refs[0]);
            } else if (failOnError) {
                throw new TaskException("Unable to find unique task for id: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task", e);
        }

        return result;
    }

    @Override
    public void publishActionClass(PropertyContainerClassContext context, String name, List<TypedProperty> properties) {
        String pluginId = context.getPluginContext().getPluginId();
        BundleContext ctx = BundleUtil.getBundleContext(getClass(), pluginId);

        if (ctx != null) {
            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            if (pluginId == null) {
                logger.error("Unable to publish action with null plugin ID");
            } else {
                props.put("pluginId", pluginId);
                props.put("type", "actionClass");
                props.put("classId", context.getContainerClassId());

                synchronized (serviceRegistrationMap) {
                    List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
                    if (srl == null) {
                        srl = new ArrayList<>();
                        serviceRegistrationMap.put(pluginId, srl);
                    }
                    srl.add(
                        ctx.registerService(
                            PropertyContainerClass.class,
                            new PropertyContainerClass(context, name, properties),
                            props
                        )
                    );
                }

                logger.debug("Action class {} published", context);
            }
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public PropertyContainerSet publishActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
        return actionSetStore.addActionSet(ctx, name, actions);
    }

    @Override
    public void publishConditionClass(PropertyContainerClassContext ctx, String name, List<TypedProperty> properties) {
        try {
            String pluginId = ctx.getPluginContext().getPluginId();
            BundleContext context = BundleUtil.getBundleContext(getClass(), pluginId);

            if (context != null) {
                // register device as a service
                Dictionary<String,String> props = new Hashtable<>();
                if (pluginId == null) {
                    logger.error("Unable to publish action with null plugin ID");
                } else {
                    props.put("pluginId", pluginId);
                    props.put("type", "conditionClass");
                    props.put("classId", ctx.getContainerClassId());

                    synchronized (serviceRegistrationMap) {
                        List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
                        if (srl == null) {
                            srl = new ArrayList<>();
                            serviceRegistrationMap.put(pluginId, srl);
                        }
                        srl.add(
                            context.registerService(
                                PropertyContainerClass.class,
                                new PropertyContainerClass(ctx, name, properties),
                                props
                            )
                        );
                    }

                    logger.debug("Condition class {} published", ctx);
                }
            } else {
                throw new HobsonRuntimeException("Unable to obtain context to publish action");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeTask(final TaskContext ctx) {
        final HobsonTask task = getTask(ctx);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Throwable error = null;
                try {
                    task.execute();
                } catch (Throwable e) {
                    error = e;
                }
                eventManager.postEvent(ctx.getPluginContext().getHubContext(), new TaskExecutionEvent(System.currentTimeMillis(), task.getName(), error));
            }
        });
    }

    @Override
    public PropertyContainerClass getActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(type=actionClass)(classId=" + ctx.getContainerClassId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (PropertyContainerClass)bundleContext.getService(refs[0]);
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId) {
        return actionSetStore.getActionSet(ctx, actionSetId);
    }

    @Override
    public Collection<PropertyContainerClass> getAllActionClasses(HubContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass))");
            List<PropertyContainerClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                for (ServiceReference ref : references) {
                    results.add((PropertyContainerClass)context.getService(ref));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    @Override
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx) {
        return actionSetStore.getAllActionSets(ctx);
    }

    @Override
    public Collection<PropertyContainerClass> getAllConditionClasses(HubContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=conditionClass))");
            List<PropertyContainerClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                for (ServiceReference ref : references) {
                    results.add((PropertyContainerClass)context.getService(ref));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving condition classes", e);
        }
    }

    @Override
    public void updateTask(final TaskContext ctx, final String name, final String description, final PropertyContainerSet conditionSet, final PropertyContainerSet actionSet) {
        final HobsonPlugin plugin = pluginManager.getPlugin(ctx.getPluginContext());
        if (plugin != null) {
            final TaskProvider provider = getTaskProvider(plugin);
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                provider.onUpdateTask(ctx, name, description, conditionSet, actionSet);
                }
            });
        } else {
            throw new TaskException("No plugin found: " + ctx);
        }
    }

    @Override
    public void fireTaskExecutionEvent(HobsonTask task, long now, Throwable error) {
        eventManager.postEvent(HubContext.createLocal(), new TaskExecutionEvent(now, task.getName(), error));
    }

    @Override
    public void createTask(HubContext ctx, String name, String description, PropertyContainerSet conditionSet, PropertyContainerSet actionSet) {
        if (conditionSet != null && conditionSet.hasPrimaryProperty()) {
            if (conditionSet.getPrimaryProperty().getContainerClassContext() != null) {
                // convert explicit action set to action set ID
                if (!actionSet.hasId()) {
                    actionSet = publishActionSet(ctx, null, actionSet.getProperties());
                }

                // send the create task request to the appropriate task provider
                HobsonPlugin plugin = pluginManager.getPlugin(conditionSet.getPrimaryProperty().getContainerClassContext().getPluginContext());
                if (plugin.getRuntime().getTaskProvider() != null) {
                    plugin.getRuntime().getTaskProvider().onCreateTask(name, description, conditionSet, actionSet);
                } else {
                    throw new HobsonRuntimeException("Plugin associated with trigger condition does not support task creation");
                }
            } else {
                throw new HobsonInvalidRequestException("Trigger condition has no condition class defined");
            }
        } else {
            throw new HobsonInvalidRequestException("No trigger condition found");
        }
    }

    @Override
    public void deleteTask(final TaskContext ctx) {
        final HobsonPlugin plugin = pluginManager.getPlugin(ctx.getPluginContext());
        if (plugin != null) {
            final TaskProvider provider = getTaskProvider(plugin);
            final HobsonTask task = getTask(ctx);
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // delete the task from the provider
                    provider.onDeleteTask(task.getContext());

                    // unpublish the task service
                    List<ServiceRegistration> regs = serviceRegistrationMap.get(ctx.getPluginId());
                    if (regs != null) {
                        for (ServiceRegistration sr : regs) {
                            // TODO
                        }
                    }
                }
            });
        } else {
            throw new TaskException("No plugin found: " + ctx);
        }
    }

    @Override
    public void executeActionSet(HubContext ctx, String actionSetId) {
        PropertyContainerSet actionSet = actionSetStore.getActionSet(ctx, actionSetId);
        if (actionSet != null) {
            for (final PropertyContainer pc : actionSet.getProperties()) {
                final HobsonPlugin plugin = pluginManager.getPlugin(pc.getContainerClassContext().getPluginContext());
                if (plugin != null) {
                    plugin.getRuntime().submitInEventLoop(new Runnable() {
                        public void run() {
                            plugin.getRuntime().onExecuteAction(pc);
                        }
                    });
                } else {
                    logger.error("Unable to execute action published by unknown plugin: " + pc.getContainerClassContext().getPluginContext());
                }
            }
        } else {
            throw new HobsonRuntimeException("Unable to find action set: " + actionSetId);
        }
    }

    private TaskProvider getTaskProvider(HobsonPlugin plugin) {
        if (plugin.getRuntime() != null) {
            if (plugin.getRuntime().getTaskProvider() != null) {
                return plugin.getRuntime().getTaskProvider();
            } else {
                throw new TaskException("No task provider available for task " + plugin.getContext());
            }
        } else {
            throw new TaskException("No plugin runtime available for task " + plugin.getContext());
        }
    }
}
