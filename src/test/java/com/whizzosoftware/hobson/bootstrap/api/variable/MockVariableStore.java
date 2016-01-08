/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.VariableContext;
import com.whizzosoftware.hobson.api.variable.VariableNotFoundException;

import java.util.*;

public class MockVariableStore implements VariableStore {
    private Map<VariableContext,HobsonVariable> variables = new HashMap<>();

    @Override
    public Collection<HobsonVariable> getAllVariables(HubContext hctx) {
        return variables.values();
    }

    @Override
    public Collection<HobsonVariable> getDeviceVariables(DeviceContext ctx) {
        List<HobsonVariable> results = new ArrayList<>();
        for (VariableContext v : variables.keySet()) {
            if (v.getDeviceContext().equals(ctx)) {
                results.add(variables.get(v));
            }
        }
        return results;
    }

    @Override
    public Collection<HobsonVariable> getAllGlobalVariables(HubContext ctx) {
        List<HobsonVariable> results = new ArrayList<>();
        for (VariableContext v : variables.keySet()) {
            if (v.getHubContext().equals(ctx) && v.isGlobal()) {
                results.add(variables.get(v));
            }
        }
        return results;
    }

    @Override
    public Collection<HobsonVariable> getPluginGlobalVariables(PluginContext ctx) {
        List<HobsonVariable> results = new ArrayList<>();
        for (VariableContext v : variables.keySet()) {
            if (v.getPluginContext().equals(ctx) && v.isGlobal()) {
                results.add(variables.get(v));
            }
        }
        return results;
    }

    @Override
    public boolean hasVariable(VariableContext ctx) {
        return (variables.containsKey(ctx));
    }

    @Override
    public HobsonVariable getVariable(VariableContext ctx) {
        HobsonVariable v = variables.get(ctx);
        if (v != null) {
            return v;
        } else {
            throw new VariableNotFoundException("Variable not found: " + ctx);
        }
    }

    @Override
    public Collection<String> getVariableNames() {
        List<String> names = new ArrayList<>();
        for (HobsonVariable v : variables.values()) {
            names.add(v.getContext().getName());
        }
        return names;
    }

    @Override
    public void publishVariable(HobsonVariable variable) {
        variables.put(variable.getContext(), variable);
    }

    @Override
    public void unpublishVariable(VariableContext ctx) {
        for (Iterator<VariableContext> it = variables.keySet().iterator(); it.hasNext(); ) {
            VariableContext v = it.next();
            if (v.equals(ctx)) {
                it.remove();
                break;
            }
        }
    }

    @Override
    public void unpublishVariables(DeviceContext ctx) {
        for (Iterator<VariableContext> it = variables.keySet().iterator(); it.hasNext(); ) {
            VariableContext v = it.next();
            if (v.getDeviceContext().equals(ctx)) {
                it.remove();
            }
        }
    }
}
