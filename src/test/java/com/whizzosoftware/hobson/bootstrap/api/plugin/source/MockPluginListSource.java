/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.plugin.HobsonPluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MockPluginListSource implements PluginListSource {
    public Map<String, HobsonPluginDescriptor> pluginMap = new HashMap<>();

    public void addPluginDescriptor(HobsonPluginDescriptor pd) {
        pluginMap.put(pd.getId(), pd);
    }

    @Override
    public Map<String, HobsonPluginDescriptor> getPlugins() {
        return pluginMap;
    }

    @Override
    public Collection<HobsonPluginDescriptor> getPlugin(PluginContext ctx) {
        return null;
    }
}
