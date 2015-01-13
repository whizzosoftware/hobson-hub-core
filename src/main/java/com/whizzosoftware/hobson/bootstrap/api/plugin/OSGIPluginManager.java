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
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.PluginConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.*;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An OSGi implementation of PluginManager.
 *
 * @author Dan Noguerol
 */
public class OSGIPluginManager implements PluginManager {
    private static final Logger logger = LoggerFactory.getLogger(OSGIPluginManager.class);

    volatile private BundleContext bundleContext;
    volatile private ConfigurationAdmin configAdmin;
    volatile private EventManager eventManager;

    private final static String DEVICE_PID_SEPARATOR = ".";

    private final Map<String,ServiceRegistration> managedServiceRegistrations = new HashMap<>();
    private final ArrayDeque<PluginRef> queuedResources = new ArrayDeque<PluginRef>();
    static ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));

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
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + HobsonPlugin.class.getName() + ")(pluginId=" + pluginId + "))");
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
    public Object getPluginConfigurationProperty(String userId, String hubId, String pluginId, String name) {
        Configuration c = getPluginConfiguration(userId, hubId, pluginId);
        if (c != null) {
            return c.getProperty(name);
        } else {
            return null;
        }
    }

    @Override
    public Configuration getPluginConfiguration(String userId, String hubId, HobsonPlugin plugin) {
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(plugin.getId());
        Dictionary props = config.getProperties();

        // build a list of ConfigurationProperty objects
        Collection<ConfigurationPropertyMetaData> metas = plugin.getConfigurationPropertyMetaData();

        Configuration ci = new Configuration();

        for (ConfigurationPropertyMetaData meta : metas) {
            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }
            ci.addProperty(new ConfigurationProperty(meta, value));
        }

        return ci;
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
        updateOSGIConfiguration(pluginId, config, props);
    }

    @Override
    public void setPluginConfigurationProperty(String userId, String hubId, String pluginId, String name, Object value) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(pluginId);
        Dictionary dic = getDictionFromOSGIConfiguration(config);

        // update the new configuration property
        dic.put(name, value);

        // save the new configuration
        updateOSGIConfiguration(pluginId, config, dic);
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
        logger.debug("Queuing plugin " + pluginId + " for installation");

        BundleContext context = FrameworkUtil.getBundle(OSGIPluginManager.class).getBundleContext();
        ServiceReference sr = context.getServiceReference(RepositoryAdmin.class.getName());
        final RepositoryAdmin repoAdmin = (RepositoryAdmin) context.getService(sr);

        synchronized (queuedResources) {
            queuedResources.add(new PluginRef(pluginId, pluginVersion));

            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        logger.debug("Plugin installer is executing");

                        PluginRef ref = nextRef();

                        while (ref != null) {
                            try {
                                deployResource(repoAdmin, findResource(repoAdmin, ref));
                            } catch (IllegalStateException ise) {
                                // this exception is caught specifically since Felix occasionally throws this exception
                                // even though the plugin can technically be installed. This appears to be the
                                // cause of the problem: https://issues.apache.org/jira/browse/FELIX-3422.
                                logger.debug("Temporary error installing plugin " + ref.symbolicName + "; will retry");
                                queuedResources.push(ref);
                            } catch (Exception e) {
                                logger.error("Failed to install plugin " + ref.symbolicName, e);
                            }

                            ref = nextRef();
                        }

                        logger.debug("Plugin installer is finished");
                    }
                });
            } catch (RejectedExecutionException ignored) {
                // The assumption here is that since the currently executing Runnable will drain the queue before
                // exiting, there is no need to have more than one pending runnable in the work queue
            }
        }
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

    private void updateOSGIConfiguration(String pluginId, org.osgi.service.cm.Configuration config, Dictionary d) {
        try {
            config.update(d);
            eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new PluginConfigurationUpdateEvent(pluginId, d));
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }

    /**
     * Returns the next PluginRef queued for installation.
     *
     * @return a PluginRef instance
     */
    private PluginRef nextRef() {
        synchronized (queuedResources) {
            return queuedResources.poll();
        }
    }

    /**
     * Deploys as OSGi resource to the runtime framework.
     *
     * @param repoAdmin the repository admin
     * @param r the repository resource to install
     */
    private void deployResource(RepositoryAdmin repoAdmin, Resource r) {
        logger.debug("Attempting to install plugin: " + r.getSymbolicName());
        Resolver resolver = repoAdmin.resolver();
        resolver.add(r);
        if (resolver.resolve()) {
            resolver.deploy(Resolver.START);
        }
        logger.info("Plugin completed installation: " + r.getSymbolicName());
    }

    /**
     * Finds a Resource in a repository.
     *
     * @param repoAdmin the repository to search
     * @param pluginRef the plugin reference
     *
     * @return a Resource instance or null if not found
     *
     * @throws InvalidSyntaxException
     */
    static private Resource findResource(RepositoryAdmin repoAdmin, PluginRef pluginRef) throws InvalidSyntaxException {
        Resource[] resources = repoAdmin.discoverResources("(symbolicname=" + pluginRef.symbolicName + ")");
        for (Resource r : resources) {
            if (pluginRef.symbolicName.equals(r.getSymbolicName()) && pluginRef.version.equals(r.getVersion().toString())) {
                return r;
            }
        }
        return null;
    }

    /**
     * A class that references a plugin ID and version string.
     */
    static class PluginRef {
        public String symbolicName;
        public String version;

        public PluginRef(String symbolicName, String version) {
            this.symbolicName = symbolicName;
            this.version = version;
        }
    }
}
