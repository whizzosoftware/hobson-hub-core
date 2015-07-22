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
import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.PluginConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.image.ImageInputStream;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGILocalPluginListSource;
import com.whizzosoftware.hobson.bootstrap.api.plugin.source.OSGIRepoPluginListSource;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.bootstrap.util.DictionaryUtil;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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

    private final ArrayDeque<PluginRef> queuedResources = new ArrayDeque<>();
    static ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));

    @Override
    public HobsonPlugin getLocalPlugin(PluginContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + HobsonPlugin.class.getName() + ")(pluginId=" + ctx.getPluginId() + "))");
            if (references != null && references.length == 1) {
                return (HobsonPlugin)context.getService(references[0]);
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate devices detected");
            } else {
                throw new HobsonNotFoundException("Unable to locate plugin: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving plugin", e);
        }
    }

    @Override
    public Collection<PluginDescriptor> getLocalPluginDescriptors(HubContext ctx) {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        return new OSGILocalPluginListSource(context).getPlugins().values();
    }

    @Override
    public Collection<PluginDescriptor> getRemotePluginDescriptors(HubContext ctx) {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        return new OSGIRepoPluginListSource(context).getPlugins().values();
    }

    @Override
    public PluginDescriptor getRemotePluginDescriptor(PluginContext ctx, String version) {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        OSGIRepoPluginListSource src = new OSGIRepoPluginListSource(context);
        for (PluginDescriptor pd : src.getPlugin(ctx)) {
            if (version.equals(pd.getVersionString())) {
                return pd;
            }
        }
        return null;
    }

    @Override
    public PropertyContainer getLocalPluginConfiguration(PluginContext ctx) {
        return getPluginConfiguration(getLocalPlugin(ctx));
    }

    @Override
    public Object getLocalPluginConfigurationProperty(PluginContext ctx, String name) {
        PropertyContainer c = getLocalPluginConfiguration(ctx);
        if (c != null) {
            return c.getPropertyValue(name);
        } else {
            return null;
        }
    }

    protected PropertyContainer getPluginConfiguration(HobsonPlugin plugin) {
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(plugin.getContext().getPluginId());
        Dictionary props = config.getProperties();

        // build a list of PropertyContainer objects
        PropertyContainerClass metas = plugin.getConfigurationClass();

        PropertyContainer ci = new PropertyContainer();

        if (metas != null && metas.hasSupportedProperties()) {
            for (TypedProperty meta : metas.getSupportedProperties()) {
                Object value = null;
                if (props != null) {
                    value = props.get(meta.getId());
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
    public ImageInputStream getLocalPluginIcon(PluginContext ctx) {
        try {
            Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
            URL url = null;
            if (bundle != null) {
                url = bundle.getResource("icon.png");
            }
            InputStream is = null;
            if (url != null) {
                is = url.openStream();
            } else {
                url = FrameworkUtil.getBundle(getClass()).getResource("default-plugin-icon.png");
                if (url != null) {
                    is = url.openStream();
                }
            }
            if (is != null) {
                return new ImageInputStream("image/png", is);
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to read plugin icon", e);
        }
    }

    @Override
    public void setLocalPluginConfiguration(PluginContext ctx, PropertyContainer newConfig) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(ctx.getPluginId());

        try {
            // update the new configuration properties
            DictionaryUtil.updateConfigurationDictionary(config, newConfig.getPropertyValues(), true);

            // save the new configuration
            postPluginConfigurationUpdateEvent(ctx);
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }

    @Override
    public void setLocalPluginConfigurationProperty(PluginContext ctx, String name, Object value) {
        // get the current configuration
        org.osgi.service.cm.Configuration config = getOSGIConfiguration(ctx.getPluginId());

        try {
            // update the new configuration property
            DictionaryUtil.updateConfigurationDictionary(config, Collections.singletonMap(name, value), true);

            // save the new configuration
            postPluginConfigurationUpdateEvent(ctx);
        } catch (IOException e) {
            throw new ConfigurationException("Error updating plugin configuration", e);
        }
    }

    @Override
    public void reloadLocalPlugin(PluginContext ctx) {
        try {
            Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
            if (bundle != null) {
                bundle.stop();
                bundle.start();
            }
        } catch (BundleException e) {
            throw new HobsonRuntimeException("Error reloading plugin: " + ctx, e);
        }
    }

    @Override
    public void installRemotePlugin(PluginContext ctx, String pluginVersion) {
        logger.debug("Queuing plugin " + ctx.getPluginId() + " for installation");

        BundleContext context = FrameworkUtil.getBundle(OSGIPluginManager.class).getBundleContext();
        ServiceReference sr = context.getServiceReference(RepositoryAdmin.class.getName());
        final RepositoryAdmin repoAdmin = (RepositoryAdmin) context.getService(sr);

        synchronized (queuedResources) {
            queuedResources.add(new PluginRef(ctx.getPluginId(), pluginVersion));

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
    public File getDataDirectory(PluginContext ctx) {
        Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
        if (bundle != null) {
            BundleContext context = bundle.getBundleContext();
            return context.getDataFile("tmp").getParentFile();
        } else {
            throw new ConfigurationException("Error obtaining data file");
        }
    }

    @Override
    public File getDataFile(PluginContext ctx, String filename) {
        Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
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

    private void postPluginConfigurationUpdateEvent(PluginContext ctx) {
        // post event
        eventManager.postEvent(
            ctx.getHubContext(),
            new PluginConfigurationUpdateEvent(
                System.currentTimeMillis(),
                ctx,
                getLocalPluginConfiguration(ctx)
            )
        );
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
