/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;

import java.util.Collection;
import java.util.List;

/**
 * An interface for variable storage.
 *
 * @author Dan Noguerol
 */
public interface VariableStore {
    List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId);
    List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId, String name);
    Collection<String> getVariableNames();
    void publishVariable(HobsonVariable variable);
    void unpublishVariable(PluginContext ctx, String deviceId, String name);
    void unpublishVariables(PluginContext ctx, String deviceId);
}
