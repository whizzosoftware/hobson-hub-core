/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.store;

import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.bootstrap.util.DictionaryUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;

/**
 * An OSGi ConfigurationAdmin based implementation of PluginConfigurationStore.
 *
 * @author Dan Noguerol
 */
public class OSGIPluginConfigurationStore implements PluginConfigurationStore {
    private BundleContext bundleContext;
    private ConfigurationAdmin configAdmin;

    public OSGIPluginConfigurationStore(BundleContext bundleContext, ConfigurationAdmin configAdmin) {
        this.bundleContext = bundleContext;
        this.configAdmin = configAdmin;
    }

    @Override
    public PropertyContainer getLocalPluginConfiguration(PluginContext ctx, PropertyContainerClass configurationClass) {
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(ctx.getPluginId());
        Dictionary props = config.getProperties();

        // build a list of PropertyContainer objects

        PropertyContainer ci = new PropertyContainer();

        if (configurationClass != null && configurationClass.hasSupportedProperties()) {
            for (TypedProperty meta : configurationClass.getSupportedProperties()) {
                Object value = null;
                if (props != null) {
                    if (meta.getType() == TypedProperty.Type.DEVICE) {
                        value = DeviceContext.create((String)props.get(meta.getId()));
                    } else {
                        value = props.get(meta.getId());
                    }
                    props.remove(meta.getId());
                }
                ci.setPropertyValue(meta.getId(), value);
            }
        }

        // On first run, the plugin will not have had its onStartup() method called and so may not have registered
        // configuration meta data yet. If there are configuration properties set for the plugin that don't have
        // corresponding meta data, create some "raw" meta data for it and add it to the returned configuration
        if (props != null && props.size() > 0) {
            Enumeration e = props.keys();
            while (e.hasMoreElements()) {
                String key = (String)e.nextElement();
                ci.setPropertyValue(key, props.get(key));
            }
        }

        return ci;
    }

    @Override
    public void setLocalPluginConfiguration(PluginContext ctx, PropertyContainerClass configurationClass, PropertyContainer newConfig) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(ctx.getPluginId());

        try {
            // update the new configuration properties
            DictionaryUtil.updateConfigurationDictionary(config, newConfig.getPropertyValues(), true);
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }

    @Override
    public void setLocalPluginConfigurationProperty(PluginContext ctx, PropertyContainerClass configurationClass, String name, Object value) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(ctx.getPluginId());

        try {
            // update the new configuration property
            DictionaryUtil.updateConfigurationDictionary(config, Collections.singletonMap(name, value), true);
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }

    @Override
    public void close() {

    }

    private org.osgi.service.cm.Configuration getOSGIConfiguration(String pluginId) {
        if (bundleContext != null) {
            try {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(pluginId);
                if (bundle != null) {
                    org.osgi.service.cm.Configuration c = configAdmin.getConfiguration(pluginId, bundle.getLocation());
                    if (c == null) {
                        throw new ConfigurationException("Unable to obtain configuration for " + pluginId + " (null)");
                    }
                    return c;
                }
            } catch (IOException e) {
                throw new ConfigurationException("Unable to obtain configuration for " + pluginId, e);
            }
        }
        throw new ConfigurationException("Unable to obtain configuration for " + pluginId);
    }
}
