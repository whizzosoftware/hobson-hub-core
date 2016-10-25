/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A PluginListSource implementation that returns plugin information about bundles installed in the OSGi runtime.
 *
 * @author Dan Noguerol
 */
public class OSGILocalPluginListSource implements PluginListSource {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BundleContext bundleContext;

    public OSGILocalPluginListSource(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public Map<String,HobsonPluginDescriptor> getPlugins() {
        Map<String,HobsonPluginDescriptor> resultMap = new HashMap<>();

        // build collection of all installed OSGi bundles
        Bundle[] bundles = bundleContext.getBundles();
        for (Bundle bundle : bundles) {
            String id = bundle.getSymbolicName();
            try {
                HobsonPlugin plugin = com.whizzosoftware.hobson.bootstrap.api.plugin.PluginUtil.getPlugin(bundle.getBundleContext(), id);
                resultMap.put(id, (plugin != null) ? plugin.getDescriptor() : createPluginDescriptor(bundle, id));
            } catch (Exception e) {
                logger.error("Error creating plugin descriptor for " + id, e);
            }
        }

        return resultMap;
    }

    @Override
    public Collection<HobsonPluginDescriptor> getPlugin(PluginContext ctx) {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (ctx.getPluginId().equals(bundle.getSymbolicName())) {
                HobsonPlugin plugin = com.whizzosoftware.hobson.bootstrap.api.plugin.PluginUtil.getPlugin(bundle.getBundleContext(), ctx.getPluginId());
                if (plugin != null) {
                    return Collections.singletonList((HobsonPluginDescriptor)plugin.getDescriptor());
                }
            }
        }
        return null;
    }

    protected HobsonPluginDescriptor createPluginDescriptor(Bundle bundle, String pluginId) {
        String name = createDisplayNameFromSymbolicName(bundle.getHeaders(), bundle.getSymbolicName());

        PluginType pluginType = PluginType.FRAMEWORK;

        // determine if this is any sort of Hobson plugin
        Dictionary headers = bundle.getHeaders();
        if (isCorePlugin(headers)) {
            pluginType = PluginType.CORE;
        } else if (isPlugin(headers)) {
            pluginType = PluginType.PLUGIN;
        }

        PluginStatus status = BundleUtil.createPluginStatusFromBundleState(bundle.getState());

        return new HobsonPluginDescriptor(
            pluginId,
            pluginType,
            name,
            (String)headers.get(Constants.BUNDLE_DESCRIPTION),
            bundle.getVersion().toString(),
            status
        );
    }

    protected boolean isCorePlugin(Dictionary headers) {
        if (headers != null) {
            String categories = (String)headers.get(Constants.BUNDLE_CATEGORY);
            return (categories != null && categories.contains("hobson-core-plugin"));
        }
        return false;
    }

    protected boolean isPlugin(Dictionary headers) {
        if (headers != null) {
            String categories = (String)headers.get(Constants.BUNDLE_CATEGORY);
            return (categories != null && categories.contains("hobson-plugin"));
        }
        return false;
    }

    protected String createDisplayNameFromSymbolicName(Dictionary bundleHeaders, String bundleSymbolicName) {
        if (bundleHeaders != null) {
            Object o = bundleHeaders.get(Constants.BUNDLE_NAME);
            if (o != null) {
                return o.toString();
            }
        }
        return bundleSymbolicName;
    }
}
