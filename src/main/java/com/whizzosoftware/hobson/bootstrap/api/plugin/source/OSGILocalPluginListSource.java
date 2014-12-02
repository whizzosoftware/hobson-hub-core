/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.PluginType;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

/**
 * A PluginListSource implementation that returns plugin information about bundles installed in the OSGi runtime.
 *
 * @author Dan Noguerol
 */
public class OSGILocalPluginListSource implements PluginListSource {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BundleContext bundleContext;

    public OSGILocalPluginListSource() {
        this(FrameworkUtil.getBundle(OSGILocalPluginListSource.class).getBundleContext());
    }

    public OSGILocalPluginListSource(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public Map<String,PluginDescriptor> getPlugins() {
        Map<String,PluginDescriptor> resultMap = new HashMap<String,PluginDescriptor>();

        // build collection of all installed OSGi bundles
        Bundle[] bundles = bundleContext.getBundles();
        for (Bundle bundle : bundles) {
            String id = bundle.getSymbolicName();
            try {
                PluginDescriptor pd = createPluginDescriptor(bundle, id);
                pd.setConfigurable(isConfigurable(bundle, id));
                resultMap.put(id, pd);
            } catch (Exception e) {
                logger.error("Error creating plugin descriptor for " + id, e);
            }
        }

        return resultMap;
    }

    public PluginDescriptor getPlugin(String pluginId) {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (pluginId.equals(bundle.getSymbolicName())) {
                return createPluginDescriptor(bundle, pluginId);
            }
        }
        return null;
    }

    protected PluginDescriptor createPluginDescriptor(Bundle bundle, String pluginId) {
        HobsonPlugin plugin = getHobsonPlugin(bundleContext, bundle.getSymbolicName());

        String name = createDisplayNameFromSymbolicName(bundle.getHeaders(), bundle.getSymbolicName());

        PluginType pluginType;
        PluginStatus pluginStatus;
        if (plugin != null) {
            pluginType = plugin.getType();
            pluginStatus = plugin.getStatus();
        } else {
            pluginType = PluginType.FRAMEWORK;
            pluginStatus = BundleUtil.createPluginStatusFromBundleState(bundle.getState());
        }

        return new PluginDescriptor(pluginId, name, null, pluginType, pluginStatus, bundle.getVersion().toString());
    }

    protected boolean isConfigurable(Bundle bundle, String pluginId) {
        HobsonPlugin plugin = com.whizzosoftware.hobson.bootstrap.api.plugin.PluginUtil.getPlugin(bundle.getBundleContext(), pluginId);
        return (plugin != null && plugin.isConfigurable());
    }

    protected String createDisplayNameFromSymbolicName(Dictionary bundleHeaders, String bundleSymbolicName) {
        if (bundleHeaders != null) {
            Object o = bundleHeaders.get("Bundle-Name");
            if (o != null) {
                return o.toString();
            }
        }
        return bundleSymbolicName;
    }

    protected HobsonPlugin getHobsonPlugin(BundleContext context, String symbolicName) {
        // determine if the bundle is a Hobson plugin
        HobsonPlugin plugin = null;

        try {
            Filter filter = context.createFilter("(&(objectClass=" + HobsonPlugin.class.getName() + ")(pluginId=" + symbolicName + "))");
            ServiceReference[] references = context.getAllServiceReferences(null, filter.toString());
            if (references != null && references.length == 1) {
                plugin = (HobsonPlugin)context.getService(references[0]);
            }
        } catch (InvalidSyntaxException ignored) {}

        return plugin;
    }
}
