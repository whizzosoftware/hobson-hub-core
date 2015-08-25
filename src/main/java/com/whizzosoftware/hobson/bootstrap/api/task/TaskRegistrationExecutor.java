/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A Runnable responsible for checking the list of unregistered tasks, checking if it is fully resolved, and
 * if so, performing the onCreateTasks() callback to the appropriate plugin.
 *
 * @author Dan Noguerol
 */
public class TaskRegistrationExecutor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TaskRegistrationExecutor.class);

    private final HubContext hubContext;
    private final TaskRegistrationContext ctx;
    /**
     * List of task contexts that have an async registration in queue.
     */
    private final List<TaskContext> pendingRegistrationList = new ArrayList<>();
    /**
     * List of task contexts that have been registered.
     */
    private final List<TaskContext> registrationList = new ArrayList<>();

    public TaskRegistrationExecutor(HubContext hubContext, TaskRegistrationContext ctx) {
        this.hubContext = hubContext;
        this.ctx = ctx;
    }

    public void run() {
        final Collection<HobsonTask> tasks = ctx.getAllTasks(hubContext);

        if (tasks != null) {
            // build a map of plugins to unregistered tasks
            Map<HobsonPlugin,List<HobsonTask>> taskMap = new HashMap<>();
            synchronized (this) {
                for (final HobsonTask task : tasks) {
                    if (!registrationList.contains(task.getContext()) && !pendingRegistrationList.contains(task.getContext()) && ctx.isTaskFullyResolved(task)) {
                        final HobsonPlugin plugin = ctx.getPluginForTask(task);
                        List<HobsonTask> taskList = taskMap.get(plugin);
                        if (taskList == null) {
                            taskList = new ArrayList<>();
                            taskMap.put(plugin, taskList);
                        }
                        taskList.add(task);
                        pendingRegistrationList.add(task.getContext());
                    }
                }
            }

            // for each plugin that has a list of resolved tasks, invoke its onCreateTasks callback
            for (final HobsonPlugin plugin : taskMap.keySet()) {
                final List<HobsonTask> taskList = taskMap.get(plugin);
                if (plugin != null && plugin.getRuntime() != null && plugin.getRuntime().getTaskProvider() != null) {
                    plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (TaskRegistrationExecutor.this) {
                                try {
                                    // perform the callback
                                    plugin.getRuntime().getTaskProvider().onCreateTasks(taskList);

                                    // flag the tasks as registered so they aren't attempted again
                                    for (HobsonTask task : taskList) {
                                        registrationList.add(task.getContext());
                                        pendingRegistrationList.remove(task.getContext());
                                    }

                                    logger.debug("Registered {} tasks with plugin {}", taskList.size(), plugin);
                                } catch (Throwable t) {
                                    logger.error("Error registering tasks with provider: " + plugin, t);

                                    // remove tasks from pending list to they can be re-attempted
                                    for (HobsonTask task : taskList) {
                                        pendingRegistrationList.remove(task.getContext());
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }
    }
}
