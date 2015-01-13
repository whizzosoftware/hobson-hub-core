/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;

import java.util.HashMap;
import java.util.Map;

public class MockPluginListSource implements PluginListSource {
    public Map<String, PluginDescriptor> pluginMap = new HashMap<>();

    public void addPluginDescriptor(PluginDescriptor pd) {
        pluginMap.put(pd.getId(), pd);
    }

    @Override
    public Map<String, PluginDescriptor> getPlugins() {
        return pluginMap;
    }
}
