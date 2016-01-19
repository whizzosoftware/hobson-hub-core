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
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.EventListener;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.task.*;
import com.whizzosoftware.hobson.api.task.action.TaskActionClass;
import com.whizzosoftware.hobson.api.task.condition.*;
import com.whizzosoftware.hobson.api.task.store.TaskStore;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.task.store.MapDBTaskStore;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * An OSGi implementation of TaskManager.
 *
 * @author Dan Noguerol
 */
public class OSGITaskManager implements TaskManager, TaskRegistrationContext {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile HubManager hubManager;
    private volatile PluginManager pluginManager;
    private volatile VariableManager variableManager;

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();
    private TaskStore taskStore;
    private TaskConditionClassProvider taskConditionClassProvider;
    private TaskConditionProcessor conditionProcessor = new TaskConditionProcessor();
    private EventListener pluginStartListener;
    /**
     * This executor is responsible for registering any unregistered tasks with the plugins that handle
     * their trigger condition.
     */
    private final ScheduledThreadPoolExecutor taskRegistrationPool = new ScheduledThreadPoolExecutor(1);
    /**
     * Class that handles the actual check for resolved tasks and makes the plugin call to register them.
     * It implements the Runnable that the taskRegistrationPool will ultimately invoke. This task is also
     * responsible for tracking which tasks have/have not been registered.
     */
    private TaskRegistrationExecutor taskRegistrationExecutor;
    /**
     * A context provided to the TaskRegistrationExecutor giving it targeted access to the task facilities
     * it requires to do its job.
     */
    private TaskRegistrationContext taskRegistrationContext;

    public void start() {
        try {
            // create the condition class provider if it hasn't already been set
            if (taskConditionClassProvider == null) {
                taskConditionClassProvider = new OSGITaskConditionClassProvider(bundleContext);
            }

            if (taskRegistrationContext == null) {
                taskRegistrationContext = this;
            }

            taskRegistrationExecutor = new TaskRegistrationExecutor(HubContext.createLocal(), taskRegistrationContext);

            synchronized (taskRegistrationPool) {
                // add listener for any plugin startups
                if (eventManager != null) {
                    pluginStartListener = new EventListener() {
                        @Override
                        public void onHobsonEvent(HobsonEvent event) {
                            // any time a plugin starts, queue up a task registration check
                            if (event.getEventId().equals(PluginStartedEvent.ID)) {
                                queueTaskRegistration();
                            }
                        }
                    };
                    eventManager.addListener(HubContext.createLocal(), pluginStartListener, new String[]{EventTopics.STATE_TOPIC});
                } else {
                    logger.error("No event manager available - will not be able to provide tasks to their plugins");
                }

                // if a task store hasn't already been injected, create a default one
                if (taskStore == null) {
                    this.taskStore = new MapDBTaskStore(
                        pluginManager.getDataFile(
                            PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                            "tasks"
                        ),
                        this
                    );
                }

                // alert any plugins if their tasks are ready for registration
                queueTaskRegistration();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void stop() {
        taskRegistrationPool.shutdown();
        if (pluginStartListener != null) {
            eventManager.removeListener(HubContext.createLocal(), pluginStartListener, new String[]{EventTopics.STATE_TOPIC});
        }
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public void setTaskStore(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public void setTaskRegistrationContext(TaskRegistrationContext taskRegistrationContext) {
        this.taskRegistrationContext = taskRegistrationContext;
    }

    protected void queueTaskRegistration() {
        synchronized (taskRegistrationPool) {
            if (taskRegistrationPool.getQueue().size() < 2) {
                taskRegistrationPool.execute(taskRegistrationExecutor);
            }
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
    public Collection<HobsonTask> getAllTasks(HubContext ctx) {
        return new ArrayList<>(taskStore.getAllTasks(ctx));
    }

    @Override
    public TaskConditionClass getConditionClass(PropertyContainerClassContext ctx) {
        return taskConditionClassProvider.getConditionClass(ctx);
    }

    @Override
    public HobsonTask getTask(TaskContext ctx) {
        return getTask(ctx, true);
    }

    protected HobsonTask getTask(TaskContext ctx, boolean failOnError) {
        HobsonTask task = taskStore.getTask(ctx);
        if (task == null && failOnError) {
            throw new HobsonNotFoundException("Task not found");
        } else {
            return task;
        }
    }

    @Override
    public void publishActionClass(TaskActionClass actionClass) {
        String pluginId = actionClass.getContext().getPluginId();
        BundleContext ctx = BundleUtil.getBundleContext(getClass(), pluginId);

        if (ctx != null) {
            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            if (pluginId == null) {
                logger.error("Unable to publish action with null plugin ID");
            } else {
                props.put("pluginId", pluginId);
                props.put("type", "actionClass");
                props.put("classId", actionClass.getContext().getContainerClassId());

                synchronized (serviceRegistrationMap) {
                    List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
                    if (srl == null) {
                        srl = new ArrayList<>();
                        serviceRegistrationMap.put(pluginId, srl);
                    }
                    srl.add(
                        ctx.registerService(
                            PropertyContainerClass.class,
                            actionClass,
                            props
                        )
                    );
                }

                queueTaskRegistration();

                logger.debug("Action class {} published", actionClass.getContext());
            }
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public PropertyContainerSet publishActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
        return taskStore.saveActionSet(ctx, name, actions);
    }

    @Override
    public void publishConditionClass(TaskConditionClass conditionClass) {
        try {
            String pluginId = conditionClass.getContext().getPluginId();
            BundleContext context = BundleUtil.getBundleContext(getClass(), pluginId);

            if (context != null) {
                // register condition class as a service
                Dictionary<String,String> props = new Hashtable<>();
                if (pluginId == null) {
                    logger.error("Unable to publish condition class with null plugin ID");
                } else {
                    props.put("pluginId", pluginId);
                    props.put("type", "conditionClass");
                    props.put("classId", conditionClass.getContext().getContainerClassId());

                    synchronized (serviceRegistrationMap) {
                        List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
                        if (srl == null) {
                            srl = new ArrayList<>();
                            serviceRegistrationMap.put(pluginId, srl);
                        }
                        srl.add(
                            context.registerService(
                                PropertyContainerClass.class,
                                conditionClass,
                                props
                            )
                        );
                    }

                    queueTaskRegistration();

                    logger.debug("Condition class {} published", conditionClass.getContext());
                }
            } else {
                throw new HobsonRuntimeException("Unable to obtain context to publish action");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public TaskActionClass getActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(type=actionClass)(classId=" + ctx.getContainerClassId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (TaskActionClass)bundleContext.getService(refs[0]);
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId) {
        return taskStore.getActionSet(ctx, actionSetId);
    }

    @Override
    public Collection<TaskActionClass> getAllActionClasses(HubContext ctx, boolean applyConstraints) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass))");
            List<TaskActionClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                Collection<String> publishedVariableNames = variableManager.getPublishedVariableNames(ctx);
                for (ServiceReference ref : references) {
                    PropertyContainerClass pcc = (PropertyContainerClass)context.getService(ref);
                    if (!applyConstraints || pcc.evaluatePropertyConstraints(publishedVariableNames)) {
                        results.add((TaskActionClass)context.getService(ref));
                    }
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    @Override
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx) {
        return taskStore.getAllActionSets(ctx);
    }

    @Override
    public Collection<TaskConditionClass> getAllConditionClasses(HubContext ctx, ConditionClassType type, boolean applyConstraints) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=conditionClass))");
            List<TaskConditionClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                Collection<String> publishedVariableNames = variableManager.getPublishedVariableNames(ctx);
                for (ServiceReference ref : references) {
                    Object o = context.getService(ref);
                    if (o instanceof TaskConditionClass) {
                        TaskConditionClass tcc = (TaskConditionClass)context.getService(ref);
                        if ((type == null || tcc.getConditionClassType() == type) && (!applyConstraints || tcc.evaluatePropertyConstraints(publishedVariableNames))) {
                            results.add(tcc);
                        }
                    }
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving condition classes", e);
        }
    }

    @Override
    public void updateTask(final TaskContext ctx, final String name, final String description, final List<PropertyContainer> conditions, final PropertyContainerSet actionSet) {
        if (conditions != null) {
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(this, conditions);
            if (triggerCondition != null) {
                final HobsonPlugin plugin = pluginManager.getLocalPlugin(triggerCondition.getContainerClassContext().getPluginContext());
                if (plugin != null) {
                    final TaskProvider provider = getTaskProvider(plugin);
                    final HobsonTask task = getTask(ctx);
                    if (task != null) {
                        // update task attributes
                        task.setName(name);
                        task.setDescription(description);
                        task.setConditions(conditions);
                        task.setActionSet(actionSet);

                        // update the task in the task store
                        taskStore.saveTask(task);

                        // alert the plugin that the task has been updated
                        plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                provider.onUpdateTask(task);
                            }
                        });
                    } else {
                        throw new HobsonInvalidRequestException("No task found to update: " + ctx);
                    }
                } else {
                    throw new TaskException("No plugin found: " + ctx);
                }
            } else {
                throw new HobsonInvalidRequestException("No trigger condition found for task: " + ctx);
            }
        } else {
            throw new HobsonInvalidRequestException("No task conditions found: " + ctx);
        }
    }

    @Override
    public void updateTaskProperties(TaskContext ctx, Map<String, Object> properties) {
        HobsonTask task = getTask(ctx);
        for (String key : properties.keySet()) {
            task.setProperty(key, properties.get(key));
        }
        taskStore.saveTask(task);
    }

    @Override
    public void createTask(HubContext ctx, String name, String description, List<PropertyContainer> conditions, PropertyContainerSet actionSet) {
        if (conditions != null) {
            // assign IDs to all conditions that don't already have one
            for (PropertyContainer pc : conditions) {
                if (!pc.hasId()) {
                    pc.setId(UUID.randomUUID().toString());
                }
            }

            // make sure task has a trigger condition
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(this, conditions);
            if (triggerCondition != null) {
                // convert explicit action set to action set ID
                if (!actionSet.hasId()) {
                    actionSet = publishActionSet(ctx, null, actionSet.getProperties());
                }

                // create task and add to task store
                final HobsonTask task = new HobsonTask(TaskContext.create(ctx, UUID.randomUUID().toString()), name, description, null, conditions, actionSet);
                taskStore.saveTask(task);

                queueTaskRegistration();
            } else {
                throw new HobsonInvalidRequestException("Trigger condition has no condition class defined");
            }
        } else {
            throw new HobsonInvalidRequestException("No task conditions found");
        }
    }

    @Override
    public void deleteTask(final TaskContext ctx) {
        final HobsonTask task = getTask(ctx);
        if (task != null) {
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(this, task.getConditions());
            if (triggerCondition != null) {
                final HobsonPlugin plugin = pluginManager.getLocalPlugin(triggerCondition.getContainerClassContext().getPluginContext());
                if (plugin != null) {
                    final TaskProvider provider = getTaskProvider(plugin);
                    plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // delete the task from the provider
                                provider.onDeleteTask(task.getContext());

                                // remove it from the task store
                                taskStore.deleteTask(task.getContext());
                            } catch (Throwable t) {
                                logger.error("Error deleting task", t);
                            }
                        }
                    });
                } else {
                    throw new TaskException("No plugin found: " + ctx);
                }
            } else {
                throw new TaskException("No trigger condition found in task: " + ctx);
            }
        } else {
            throw new HobsonInvalidRequestException("Task not found: " + ctx);
        }
    }

    @Override
    public void fireTaskTrigger(final TaskContext ctx) {
        logger.debug("Task trigger fired: {}", ctx);

        Throwable error = null;
        try {
            // get the task
            HobsonTask task = getTask(ctx);

            if (conditionProcessor.evaluate(OSGITaskManager.this, task, variableManager, ctx)) {
                logger.debug("Executing action set for task: {}", ctx);
                executeActionSet(task.getContext().getHubContext(), task.getActionSet().getId());
            }
        } catch (Throwable e) {
            logger.error("Error firing task trigger", e);
            error = e;
        }
        eventManager.postEvent(ctx.getHubContext(), new TaskExecutionEvent(System.currentTimeMillis(), ctx, error));
    }

    @Override
    public void executeActionSet(HubContext ctx, String actionSetId) {
        PropertyContainerSet actionSet = taskStore.getActionSet(ctx, actionSetId);
        if (actionSet != null) {
            for (final PropertyContainer pc : actionSet.getProperties()) {
                final HobsonPlugin plugin = pluginManager.getLocalPlugin(pc.getContainerClassContext().getPluginContext());
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

    @Override
    public boolean isTaskFullyResolved(HobsonTask task) {
        Collection<PropertyContainerClassContext> deps = task.getDependencies(new OSGITaskActionClassProvider(bundleContext, taskStore));
        try {
            for (PropertyContainerClassContext pccc : deps) {
                Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + pccc.getPluginContext().getPluginId() + ")(classId=" + pccc.getContainerClassId() + "))");
                ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
                if (refs != null && refs.length == 1) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Error trying to check resolved task", e);
        }
        return false;
    }

    @Override
    public HobsonPlugin getPluginForTask(HobsonTask task) {
        PropertyContainer pc = TaskHelper.getTriggerCondition(OSGITaskManager.this, task.getConditions());
        if (pc != null) {
            return pluginManager.getLocalPlugin(pc.getContainerClassContext().getPluginContext());
        }
        return null;
    }
}
