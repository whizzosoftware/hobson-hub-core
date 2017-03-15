/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonLocalPluginDescriptor;
import com.whizzosoftware.hobson.api.task.HobsonTask;

import java.util.Collection;

public interface TaskRegistrationContext {
    /**
     * Returns a collection of all tasks that the runtime has not yet registered.
     *
     * @param ctx a hub context
     *
     * @return a Collection of HobsonTask instances (or null if there are no unregistered tasks)
     */
    Collection<HobsonTask> getTasks(HubContext ctx);

    /**
     * Indicates that a task is "fully resolved" -- meaning all its condition and action classes have been
     * published to the runtime.
     *
     * @param task the task to check
     *
     * @return a boolean
     */
    boolean isTaskFullyResolved(HobsonTask task);

    /**
     * Returns the plugin associated with a task -- this is based on the condition class of its trigger condition.
     *
     * @param task the task
     *
     * @return a HobsonPlugin instance (or null if no associated plugin was found)
     */
    HobsonLocalPluginDescriptor getPluginForTask(HobsonTask task);
}
