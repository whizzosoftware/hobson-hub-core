/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.TaskRegistrationEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonLocalPluginDescriptor;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A Runnable responsible for checking the list of unregistered tasks, checking if it is fully resolved, and
 * if so, sending out a TaskRegistrationEvent event.
 *
 * @author Dan Noguerol
 */
public class TaskRegistrationExecutor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TaskRegistrationExecutor.class);

    private final HubContext hubContext;
    private final EventManager eventManager;
    private final TaskRegistrationContext ctx;
    /**
     * List of task contexts that have an async registration in queue.
     */
    private final List<TaskContext> pendingRegistrationList = new ArrayList<>();
    /**
     * List of task contexts that have been registered.
     */
    private final List<TaskContext> registrationList = new ArrayList<>();

    public TaskRegistrationExecutor(HubContext hubContext, EventManager eventManager, TaskRegistrationContext ctx) {
        this.hubContext = hubContext;
        this.eventManager = eventManager;
        this.ctx = ctx;
    }

    public void run() {
        final Collection<HobsonTask> tasks = ctx.getTasks(hubContext);

        if (tasks != null) {
            logger.trace("Task registration executor is running with {} total tasks, {} pending tasks and {} registered tasks", tasks.size(), pendingRegistrationList.size(), registrationList.size());

            // build a map of plugins to unregistered tasks
            Map<HobsonLocalPluginDescriptor,List<TaskContext>> taskMap = new HashMap<>();
            synchronized (this) {
                for (final HobsonTask task : tasks) {
                    if (!registrationList.contains(task.getContext()) && !pendingRegistrationList.contains(task.getContext()) && ctx.isTaskFullyResolved(task)) {
                        final HobsonLocalPluginDescriptor plugin = ctx.getPluginForTask(task);
                        List<TaskContext> taskList = taskMap.get(plugin);
                        if (taskList == null) {
                            taskList = new ArrayList<>();
                            taskMap.put(plugin, taskList);
                        }
                        taskList.add(task.getContext());
                        pendingRegistrationList.add(task.getContext());
                    }
                }
            }

            logger.trace("Found {} unregistered tasks", taskMap.size());

            // for each plugin that has a list of resolved tasks, invoke its onCreateTasks callback
            for (final HobsonLocalPluginDescriptor plugin : taskMap.keySet()) {
                final List<TaskContext> taskList = taskMap.get(plugin);
                logger.trace("Registering tasks for plugin {}", plugin.getContext());
                try {
                    eventManager.postEvent(hubContext, new TaskRegistrationEvent(System.currentTimeMillis(), taskList));
                    // flag the tasks as registered so they aren't attempted again
                    synchronized (TaskRegistrationExecutor.this) {
                        for (TaskContext task : taskList) {
                            registrationList.add(task);
                            pendingRegistrationList.remove(task);
                        }
                        logger.debug("Registered {} tasks with plugin {}", taskList.size(), plugin);
                    }
                } catch (Throwable t) {
                    logger.error("Error sending task registration event", t);

                    // remove tasks from pending list to they can be re-attempted
                    for (TaskContext task : taskList) {
                        pendingRegistrationList.remove(task);
                    }
                }
            }
        } else {
            logger.trace("No tasks found - task registration executor stopping");
        }
    }
}
