/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.bootstrap.util.DictionaryUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;

/**
 * An OSGi ConfigurationAdmin based implementation of DeviceConfigurationStore.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceConfigurationStore implements DeviceConfigurationStore {
    private final static String DEVICE_PID_SEPARATOR = ".";

    private BundleContext bundleContext;
    private ConfigurationAdmin configAdmin;

    public OSGIDeviceConfigurationStore(BundleContext bundleContext, ConfigurationAdmin configAdmin) {
        this.bundleContext = bundleContext;
        this.configAdmin = configAdmin;
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx, PropertyContainerClass configurationClass, String name) {
        org.osgi.service.cm.Configuration config = getOSGIDeviceConfiguration(ctx);
        Dictionary props = config.getProperties();

        // build a list of PropertyContainer objects
        PropertyContainer ci = new PropertyContainer();
        for (TypedProperty meta : configurationClass.getSupportedProperties()) {
            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }

            // if the name property is null, use the default device name
            if ("name".equals(meta.getId()) && value == null) {
                value = name;
            }

            ci.setPropertyValue(meta.getId(), value);
        }

        ci.setContainerClassContext(configurationClass.getContext());

        return ci;
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        try {
            for (Bundle bundle : bundleContext.getBundles()) {
                if (ctx.getPluginId().equals(bundle.getSymbolicName())) {
                    if (configAdmin != null) {
                        org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(ctx.getPluginId() + "." + ctx.getDeviceId(), bundle.getLocation());
                        Dictionary dic = config.getProperties();
                        if (dic != null) {
                            return dic.get(name);
                        }
                    } else {
                        throw new ConfigurationException("Unable to get device configuration property: ConfigurationAdmin service is not available");
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new ConfigurationException("Error obtaining configuration", e);
        }
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, PropertyContainerClass configurationClass, String deviceName, Map<String, Object> values, boolean overwrite) {
        try {
            if (configAdmin != null) {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());

                if (bundle != null) {
                    // get configuration
                    org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(ctx.getPluginId() + "." + ctx.getDeviceId(), bundle.getLocation());

                    // update configuration dictionary
                    DictionaryUtil.updateConfigurationDictionary(config, values, overwrite);
                } else {
                    throw new ConfigurationException("Unable to set device configuration: no bundle found for " + ctx.getPluginId());
                }
            } else {
                throw new ConfigurationException("Unable to set device configuration: ConfigurationAdmin service is not available");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error obtaining configuration", e);
        }
    }

    @Override
    public void close() {

    }

    private org.osgi.service.cm.Configuration getOSGIDeviceConfiguration(DeviceContext ctx) {
        if (bundleContext != null) {
            try {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
                if (bundle != null) {
                    org.osgi.service.cm.Configuration c = configAdmin.getConfiguration(getDevicePID(ctx.getPluginId(), ctx.getDeviceId()), bundle.getLocation());
                    if (c == null) {
                        throw new ConfigurationException("Unable to obtain configuration for: " + ctx + " (null)");
                    }
                    return c;
                }
            } catch (IOException e) {
                throw new ConfigurationException("Unable to obtain configuration for: " + ctx, e);
            }
        }
        throw new ConfigurationException("Unable to obtain configuration for: " + ctx);
    }

    private String getDevicePID(String pluginId, String deviceId) {
        return pluginId + DEVICE_PID_SEPARATOR + deviceId;
    }
}
