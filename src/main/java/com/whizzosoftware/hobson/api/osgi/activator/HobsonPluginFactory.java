/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.api.osgi.activator;

import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.bootstrap.api.plugin.HobsonPluginEventLoopWrapper;
import org.osgi.framework.BundleContext;

import java.lang.reflect.Constructor;

/**
 * A factory for instantiating HobsonPlugin subclasses. This allows us to pass the plugin ID in the constructor
 * so we can avoid having a confusing (and potentially dangerous) setId() method in the HobsonPlugin interface.
 *
 * @author Dan Noguerol
 */
public class HobsonPluginFactory {
    private BundleContext context;
    private String pluginClass;
    private String pluginId;

    public HobsonPluginFactory(BundleContext context, String pluginClass, String pluginId) {
        this.context = context;
        this.pluginId = pluginId;
        this.pluginClass = pluginClass;
    }

    public HobsonPlugin create() throws Exception {
        // TODO: should we specifically handle the lack of an appropriate constructor here or let it bubble up?
        Class clazz = context.getBundle().loadClass(pluginClass);
        Constructor c = clazz.getConstructor(String.class, String.class, String.class);
        return new HobsonPluginEventLoopWrapper((HobsonPlugin)c.newInstance(pluginId, context.getBundle().getVersion().toString(), context.getBundle().getHeaders().get("Bundle-Description")));
    }
}
