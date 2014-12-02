/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.Configuration;
import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.config.ConfigurationProperty;
import com.whizzosoftware.hobson.api.config.ConfigurationPropertyMetaData;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.*;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedService;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An OSGi implementation of PluginManager.
 *
 * @author Dan Noguerol
 */
public class OSGIPluginManager implements PluginManager {
    volatile private BundleContext bundleContext;
    volatile private ConfigurationAdmin configAdmin;

    private final static String DEVICE_PID_SEPARATOR = ".";

    private final Map<String,ServiceRegistration> managedServiceRegistrations = new HashMap<>();

    @Override
    public PluginList getPlugins(String userId, String hubId, boolean includeRemoteInfo) {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGIRepoPluginListSource remoteSource = null;
        if (includeRemoteInfo) {
            remoteSource = new com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGIRepoPluginListSource(context);
        }
        PluginListBuilder builder = new PluginListBuilder(new com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGILocalPluginListSource(context), remoteSource);
        return builder.createPluginList();
    }

    @Override
    public HobsonPlugin getPlugin(String userId, String hubId, String pluginId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + HobsonPlugin.class.getName() + ")(pluginId=" + pluginId + "))");
            if (references != null && references.length == 1) {
                return (HobsonPlugin)context.getService(references[0]);
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate devices detected");
            } else {
                throw new HobsonNotFoundException("Unable to locate plugin: " + pluginId);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving plugin", e);
        }
    }

    @Override
    public Configuration getPluginConfiguration(String userId, String hubId, String pluginId) {
        return getPluginConfiguration(userId, hubId, getPlugin(userId, hubId, pluginId));
    }

    @Override
    public Configuration getPluginConfiguration(String userId, String hubId, HobsonPlugin plugin) {
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(plugin.getId());
        Dictionary props = config.getProperties();

        // build a list of ConfigurationProperty objects
        Collection<ConfigurationPropertyMetaData> metas = plugin.getConfigurationPropertyMetaData();

        List<ConfigurationProperty> properties = new ArrayList<>();

        for (ConfigurationPropertyMetaData meta : metas) {
            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }
            properties.add(new ConfigurationProperty(meta, value));
        }

        return new Configuration(properties);
    }

    @Override
    public void setPluginConfiguration(String userId, String hubId, String pluginId, Configuration newConfig) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(pluginId);
        Dictionary props = getDictionFromOSGIConfiguration(config);

        // update the new configuration properties
        for (ConfigurationProperty p : newConfig.getProperties()) {
            props.put(p.getId(), p.getValue());
        }

        // save the new configuration
        updateOSGIConfiguration(config, props);
    }

    @Override
    public void setPluginConfigurationProperty(String userId, String hubId, String pluginId, String name, Object value) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(pluginId);
        Dictionary dic = getDictionFromOSGIConfiguration(config);

        // update the new configuration property
        dic.put(name, value);

        // save the new configuration
        updateOSGIConfiguration(config, dic);
    }

    @Override
    public String getPluginCurrentVersion(String userId, String hubId, String pluginId) {
        com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGILocalPluginListSource source = new com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGILocalPluginListSource();
        PluginDescriptor pd = source.getPlugin(pluginId);
        String currentVersion = "0.0.0";
        if (pd != null) {
            currentVersion = pd.getCurrentVersionString();
        }
        return currentVersion;
    }

    @Override
    public void reloadPlugin(String userId, String hubId, String pluginId) {
        try {
            Bundle bundle = BundleUtil.getBundleForSymbolicName(pluginId);
            if (bundle != null) {
                bundle.stop();
                bundle.start();
            }
        } catch (BundleException e) {
            throw new HobsonRuntimeException("Error reloading plugin: " + pluginId, e);
        }
    }

    @Override
    public void installPlugin(String userId, String hubId, String pluginId, String pluginVersion) {

    }

    @Override
    public File getDataFile(String pluginId, String filename) {
        Bundle bundle = BundleUtil.getBundleForSymbolicName(pluginId);
        if (bundle != null) {
            BundleContext context = bundle.getBundleContext();
            return context.getDataFile(filename);
        } else {
            throw new ConfigurationException("Error obtaining data file");
        }
    }

    @Override
    public void registerForPluginConfigurationUpdates(String pluginId, final PluginConfigurationListener listener) {
        synchronized (managedServiceRegistrations) {
            Dictionary props = new Hashtable();
            props.put("service.pid", pluginId);
            BundleContext context = BundleUtil.getBundleForSymbolicName(pluginId).getBundleContext();
            managedServiceRegistrations.put(
                    pluginId,
                    context.registerService(ManagedService.class.getName(), new ManagedService() {
                        @Override
                        public void updated(Dictionary config) throws org.osgi.service.cm.ConfigurationException {
                            if (config == null) {
                                config = new Hashtable();
                            }
                            listener.onPluginConfigurationUpdate(config);
                        }
                    }, props)
            );
        }
    }

    @Override
    public void unregisterForPluginConfigurationUpdates(String pluginId, PluginConfigurationListener listener) {
        synchronized (managedServiceRegistrations) {
            // build a list of keys that need to be unregistered and removed
            List<String> removalSet = new ArrayList<>();
            String pluginDevicePrefix = pluginId + DEVICE_PID_SEPARATOR;
            for (String key : managedServiceRegistrations.keySet()) {
                if (key.equals(pluginId) || key.startsWith(pluginDevicePrefix)) {
                    removalSet.add(key);
                }
            }

            // unregister and remove keys
            for (String key : removalSet) {
                ServiceRegistration reg = managedServiceRegistrations.get(key);
                reg.unregister();
                managedServiceRegistrations.remove(key);
            }
        }
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

    private Dictionary getDictionFromOSGIConfiguration(org.osgi.service.cm.Configuration config) {
        Dictionary d = config.getProperties();
        if (d == null) {
            d = new Hashtable();
        }
        return d;
    }

    private void updateOSGIConfiguration(org.osgi.service.cm.Configuration config, Dictionary d) {
        try {
            config.update(d);
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }
}
