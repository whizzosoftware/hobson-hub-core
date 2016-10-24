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
import java.util.Map;

/**
 * An interface for a source of plugin information.
 *
 * @author Dan Noguerol
 */
public interface PluginListSource {
    /**
     * Returns a Map of plugin descriptors.
     *
     * @return a Map
     */
    Map<String,HobsonPluginDescriptor> getPlugins();

    /**
     * Returns a specific plugin descriptor. Note that there may be more than one
     * if multiple versions exist.
     *
     * @param ctx the context of the plugin
     *
     * @return a PluginDescriptor instance (or null if not found)
     */
    Collection<HobsonPluginDescriptor> getPlugin(PluginContext ctx);
}
