/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.TaskManager;

import java.util.Collection;
import java.util.List;

/**
 * An interface for a task storage mechanism.
 *
 * @author Dan Noguerol
 */
public interface TaskStore {
    public Collection<HobsonTask> getAllTasks();
    public Collection<HobsonTask> getAllTasks(TaskManager taskManager, PluginContext pctx);
    public HobsonTask getTask(TaskContext context);
    public HobsonTask addTask(HobsonTask task);
    public void deleteTask(TaskContext context);
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx);
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId);
    public PropertyContainerSet addActionSet(HubContext ctx, String name, List<PropertyContainer> actions);
    public void deleteActionSet(String actionSetId);
    public void close();
}
