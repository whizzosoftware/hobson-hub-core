/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonInvalidRequestException;
import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.plugin.PluginStartedEvent;
import com.whizzosoftware.hobson.api.event.task.TaskCreatedEvent;
import com.whizzosoftware.hobson.api.event.task.TaskDeletedEvent;
import com.whizzosoftware.hobson.api.event.task.TaskExecutionEvent;
import com.whizzosoftware.hobson.api.event.task.TaskUpdatedEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.task.*;
import com.whizzosoftware.hobson.api.task.condition.*;
import com.whizzosoftware.hobson.api.task.store.TaskStore;
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
    private volatile ActionManager actionManager;
    private volatile EventManager eventManager;
    private volatile HubManager hubManager;
    private volatile DeviceManager deviceManager;
    private volatile PluginManager pluginManager;

    private TaskStore taskStore;
    private TaskConditionClassProvider taskConditionClassProvider;
    private TaskConditionProcessor conditionProcessor = new TaskConditionProcessor();
    /**
     * This executor is responsible for registering any unregistered tasks with the plugins that handle
     * their trigger condition. This has do be done asynchronously and monitored continuously because a
     * task can be registered for a plugin that has not yet been started by the runtime (e.g. at system
     * startup).
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

            taskRegistrationExecutor = new TaskRegistrationExecutor(HubContext.createLocal(), eventManager, taskRegistrationContext);

            synchronized (taskRegistrationPool) {
                // add listener for any plugin startups
                if (eventManager != null) {
                    eventManager.addListener(HubContext.createLocal(), this);
                } else {
                    logger.error("No event manager available - will not be able to provide tasks to their plugins");
                }

                // if a task store hasn't already been injected, create a default one
                if (taskStore == null) {
                    this.taskStore = new MapDBTaskStore(
                        pluginManager.getDataFile(
                            PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                            "tasks"
                        )
                    );
                }

                // alert any plugins if their tasks are ready for registration
                queueTaskRegistration();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @EventHandler
    public void handle(PluginStartedEvent event) {
        // any time a plugin starts, queue up a task registration check
        if (event.getEventId().equals(PluginStartedEvent.ID)) {
            logger.debug("Detected plugin start: {}", event.getContext());
            queueTaskRegistration();
        }
    }

    public void stop() {
        taskRegistrationPool.shutdown();
        if (eventManager != null) {
            eventManager.removeListener(HubContext.createLocal(), this);
        }
    }

    public void setActionManager(ActionManager actionManager) {
        this.actionManager = actionManager;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
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

    public void setTaskRegistrationExecutor(TaskRegistrationExecutor taskRegistrationExecutor) {
        this.taskRegistrationExecutor = taskRegistrationExecutor;
    }

    public void setTaskConditionClassProvider(TaskConditionClassProvider taskConditionClassProvider) {
        this.taskConditionClassProvider = taskConditionClassProvider;
    }

    protected void queueTaskRegistration() {
        synchronized (taskRegistrationPool) {
            if (taskRegistrationExecutor != null) {
                if (taskRegistrationPool.getQueue().size() < 2) {
                    taskRegistrationPool.execute(taskRegistrationExecutor);
                } else {
                    logger.trace("Skipping queued task execution");
                }
            } else {
                throw new HobsonRuntimeException("No task registration executor defined");
            }
        }
    }

    @Override
    public Collection<HobsonTask> getTasks(HubContext ctx) {
        List<HobsonTask> tasks = new ArrayList<>();
        for (TaskContext tctx : taskStore.getAllTasks(ctx)) {
            tasks.add(getTask(tctx));
        }
        return tasks;
    }

    @Override
    public TaskConditionClass getConditionClass(PropertyContainerClassContext ctx) {
        return taskConditionClassProvider != null ? taskConditionClassProvider.getConditionClass(ctx) : null;
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
            if (task != null && task.getActionSet() != null && task.getActionSet().hasId() && !task.getActionSet().hasProperties()) {
                task.setActionSet(actionManager.getActionSet(ctx.getHubContext(), task.getActionSet().getId()));
            }
            return task;
        }
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

                    context.registerService(
                            PropertyContainerClass.class,
                            conditionClass,
                            props
                    );

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
    public Collection<TaskConditionClass> getConditionClasses(HubContext ctx, ConditionClassType type, boolean applyConstraints) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=conditionClass))");
            List<TaskConditionClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                Collection<String> publishedVariableNames = deviceManager.getDeviceVariableNames(ctx);
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
    public void updateTask(final TaskContext ctx, final String name, final String description, final boolean enabled, final List<PropertyContainer> conditions, final PropertyContainerSet actionSet) {
        if (conditions != null) {
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(this, conditions);
            if (triggerCondition != null) {
                final HobsonTask task = getTask(ctx);
                if (task != null) {
                    // update task attributes
                    task.setName(name);
                    task.setDescription(description);
                    task.setEnabled(enabled);
                    task.setConditions(conditions);
                    task.setActionSet(actionSet);

                    // update the task in the task store
                    taskStore.saveTask(task);

                    // fire an update event
                    eventManager.postEvent(ctx.getHubContext(), new TaskUpdatedEvent(System.currentTimeMillis(), task.getContext()));
                } else {
                    throw new HobsonInvalidRequestException("No task found to update: " + ctx);
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
            TaskContext tctx = TaskContext.create(ctx, UUID.randomUUID().toString());

            // assign IDs to all conditions that don't already have one
            for (PropertyContainer pc : conditions) {
                if (!pc.hasId()) {
                    pc.setId(UUID.randomUUID().toString());
                }
            }

            // make sure task has a trigger condition
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(this, conditions);
            if (triggerCondition != null) {
                // create the action set if needed
                if (!actionSet.hasId()) {
                    actionSet = actionManager.publishActionSet(ctx, actionSet.getName(), actionSet.getProperties());
                }

                // create task
                final HobsonTask task = new HobsonTask(tctx, name, description, true, null, conditions, actionSet);

                // save the task
                taskStore.saveTask(task);

                // queue the task registration
                queueTaskRegistration();

                // fire an update event
                if (eventManager != null) {
                    eventManager.postEvent(ctx, new TaskCreatedEvent(System.currentTimeMillis(), task.getContext()));
                } else {
                    logger.error("Unable to post task creation event - no event manager available");
                }
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
                final HobsonLocalPluginDescriptor plugin = pluginManager.getLocalPlugin(triggerCondition.getContainerClassContext().getPluginContext());
                if (plugin != null) {
                    // remove it from the task store
                    taskStore.deleteTask(task.getContext());

                    // post the deleted event
                    eventManager.postEvent(ctx.getHubContext(), new TaskDeletedEvent(System.currentTimeMillis(), ctx));
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
    public void executeTask(TaskContext taskContext) {
        HobsonTask task = getTask(taskContext);
        actionManager.executeActionSet(task.getActionSet());
    }

    @Override
    public void fireTaskTrigger(final TaskContext ctx) {
        logger.debug("Task trigger fired: {}", ctx);

        try {
            // get the task
            HobsonTask task = getTask(ctx);

            if (conditionProcessor.evaluate(OSGITaskManager.this, task, hubManager, deviceManager, ctx)) {
                logger.debug("Executing action set for task: {}", ctx);
                actionManager.executeActionSet(task.getActionSet());
                eventManager.postEvent(ctx.getHubContext(), new TaskExecutionEvent(System.currentTimeMillis(), ctx, null));
            }
        } catch (Throwable e) {
            logger.error("Error firing task trigger", e);
            eventManager.postEvent(ctx.getHubContext(), new TaskExecutionEvent(System.currentTimeMillis(), ctx, e));
        }
    }

    @Override
    public boolean isTaskFullyResolved(HobsonTask task) {
        Collection<PropertyContainerClassContext> deps = task.getDependencies(new OSGIActionClassProvider(bundleContext, actionManager));
        for (PropertyContainerClassContext pccc : deps) {
            if (!hubManager.hasPropertyContainerClass(pccc)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public HobsonLocalPluginDescriptor getPluginForTask(HobsonTask task) {
        PropertyContainer pc = TaskHelper.getTriggerCondition(OSGITaskManager.this, task.getConditions());
        if (pc != null) {
            return pluginManager.getLocalPlugin(pc.getContainerClassContext().getPluginContext());
        }
        return null;
    }
}
