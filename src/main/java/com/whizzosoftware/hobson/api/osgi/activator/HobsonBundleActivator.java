/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.api.osgi.activator;

import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

/**
 * An OSGi bundle activator that instantiates a plugin class (defined by the Provide-Capability OSGi manifest header)
 * and registers all necessary service dependencies. This is most likely all that third-party plugins will need but it
 * can be overridden if necessary.
 *
 * @author Dan Noguerol
 */
public class HobsonBundleActivator extends DependencyActivatorBase {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        // get the Hobson plugin class from the OSGi bundle manifest
        String pluginClass = getHobsonPluginClass((String)context.getBundle().getHeaders().get("Provide-Capability"));
        if (pluginClass != null) {
            final String pluginId = context.getBundle().getSymbolicName();

            logger.debug("Loading plugin: {}", pluginId);

            // register plugin as a service with all necessary dependencies
            Component c = manager.createComponent();
            Properties props = new Properties();
            props.setProperty("pluginId", pluginId);
            c.setInterface(HobsonPlugin.class.getName(), props);
            c.setFactory(new HobsonPluginFactory(context, pluginClass, pluginId), "create");
            c.add(createServiceDependency().setService(ActionManager.class).setRequired(true));
            c.add(createServiceDependency().setService(DeviceManager.class).setRequired(true));
            c.add(createServiceDependency().setService(DiscoManager.class).setRequired(true));
            c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
            c.add(createServiceDependency().setService(HubManager.class).setRequired(true));
            c.add(createServiceDependency().setService(PluginManager.class).setRequired(true));
            c.add(createServiceDependency().setService(TaskManager.class).setRequired(true));
            c.add(createServiceDependency().setService(VariableManager.class).setRequired(true));
            c.add(createServiceDependency().setService(ExecutorService.class).setRequired(true));
            manager.add(c);
        } else {
            logger.error("No hobson.plugin provided capability found for plugin {}", context.getBundle().getSymbolicName());
        }
    }

    @Override
    public void destroy(BundleContext bundleContext, DependencyManager dependencyManager) throws Exception {
    }

    protected String getHobsonPluginClass(String providedCapabilityString) {
        int ix = providedCapabilityString.indexOf("hobson.plugin=");
        if (ix > -1) {
            return providedCapabilityString.substring(ix + 14, providedCapabilityString.length());
        } else {
            return null;
        }
    }
}
