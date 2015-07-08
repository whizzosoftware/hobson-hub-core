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

import java.util.ArrayList;
import java.util.List;

public class MockVariableStore implements VariableStore {
    private List<HobsonVariable> variables = new ArrayList<>();

    @Override
    public List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId, String name) {
        List<HobsonVariable> results = new ArrayList<>();
        for (HobsonVariable v : variables) {
            if (matches(v, pluginId, deviceId, name)) {
                results.add(v);
            }
        }
        return results;
    }

    @Override
    public List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId) {
        List<HobsonVariable> results = new ArrayList<>();
        for (HobsonVariable v : variables) {
            if (matches(v, pluginId, deviceId, null)) {
                results.add(v);
            }
        }
        return results;
    }

    @Override
    public void publishVariable(HobsonVariable variable) {
        variables.add(variable);
    }

    @Override
    public void unpublishVariable(PluginContext ctx, String deviceId, String name) {
        List<HobsonVariable> removals = new ArrayList<>();

        for (HobsonVariable v : variables) {
            if (matches(v, ctx.getPluginId(), deviceId, name)) {
                removals.add(v);
            }
        }

        for (HobsonVariable v : removals) {
            variables.remove(v);
        }
    }

    @Override
    public void unpublishVariables(PluginContext ctx, String deviceId) {
        List<HobsonVariable> removals = new ArrayList<>();

        for (HobsonVariable v : variables) {
            if (matches(v, ctx.getPluginId(), deviceId, null)) {
                removals.add(v);
            }
        }

        for (HobsonVariable v : removals) {
            variables.remove(v);
        }
    }

    private boolean matches(HobsonVariable v, String pluginId, String deviceId, String name) {
        return ((pluginId == null || v.getPluginId().equals(pluginId)) && (deviceId == null || v.getDeviceId().equals(deviceId)) && (name == null || v.getName().equals(name)));
    }
}
