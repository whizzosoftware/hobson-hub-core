/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.PluginType;
import com.whizzosoftware.hobson.api.util.VersionUtil;
import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.*;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A PluginListSource implementation that returns plugin information about bundles available in the OBR repository
 * through the RepositoryAdmin service.
 *
 * @author Dan Noguerol
 */
public class OSGIRepoPluginListSource implements PluginListSource {
    private static final Logger logger = LoggerFactory.getLogger(OSGIRepoPluginListSource.class);

    private BundleContext bundleContext;
    private Map<String,String> installedPlugins;

    /**
     * Constructor.
     *
     * @param bundleContext the bundle context
     * @param installedPlugins a Map of plugin id -> version string for all installed plugins
     */
    public OSGIRepoPluginListSource(BundleContext bundleContext, Collection<PluginDescriptor> installedPlugins) {
        this.bundleContext = bundleContext;

        // build in installed plugin name to version string map
        if (installedPlugins != null) {
            this.installedPlugins = new HashMap<>();
            for (PluginDescriptor pd : installedPlugins) {
                this.installedPlugins.put(pd.getId(), pd.getVersionString());
            }
        }
    }

    @Override
    public Map<String, PluginDescriptor> getPlugins() {
        // try to determine latest version of each bundle from repository
        ServiceReference ref = bundleContext.getServiceReference(RepositoryAdmin.class.getName());
        RepositoryAdmin repoAdmin = (RepositoryAdmin)bundleContext.getService(ref);
        try {
            return getPlugins(repoAdmin.discoverResources("(symbolicname=*)"));
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving plugins", e);
        }
    }

    @Override
    public Collection<PluginDescriptor> getPlugin(PluginContext ctx) {
        return null;
    }

    protected Map<String,PluginDescriptor> getPlugins(Resource[] resources) {
        Capability[] apiCapabilities = getAPICapabilities();

        Map<String,PluginDescriptor> resultMap = new HashMap<>();

        for (Resource repoResource : resources) {
            String sname = repoResource.getSymbolicName();

            PluginType pluginType = PluginType.FRAMEWORK;
            if (isCorePlugin(repoResource)) {
                pluginType = PluginType.CORE;
            } else if (isPlugin(repoResource)) {
                pluginType = PluginType.PLUGIN;
            }

            // check that plugin requirements are satisfied by installed capabilities
            try {
                if (isSatisfied(getAPIRequirement(repoResource), apiCapabilities)) {
                    // get the last processed version number for this plugin
                    String lastVersionString = resultMap.get(sname) != null ? resultMap.get(sname).getVersionString() : null;
                    // if there was no last processed version number, get the currently installed version
                    if (lastVersionString == null && installedPlugins != null) {
                        lastVersionString = installedPlugins.get(sname);
                    }
                    // only add the remote plugin to the result list if its version is newer than what is currently known
                    if (lastVersionString == null || VersionUtil.versionCompare(lastVersionString, repoResource.getVersion().toString()) < 0) {
                        PluginDescriptor pd = new PluginDescriptor(sname, repoResource.getPresentationName(), buildRepoDescription(repoResource), pluginType, PluginStatus.notInstalled(), null);
                        pd.setVersionString(repoResource.getVersion().toString());
                        resultMap.put(sname, pd);
                    }
                }
            } catch (InvalidSyntaxException ise) {
                logger.debug("Ignoring resource due to bad filter string", ise);
            }
        }

        return resultMap;
    }

    protected String buildRepoDescription(Resource resource) {
        String description = null;
        if (resource.getProperties() != null) {
            Object o = resource.getProperties().get("description");
            if (o != null) {
                description = o.toString();
            }
        }
        return description;
    }

    protected boolean isCorePlugin(Resource resource) {
        for (String c : resource.getCategories()) {
            if (c.contains("hobson-core-plugin")) {
                return true;
            }
        }
        return false;
    }

    protected boolean isPlugin(Resource resource) {
        for (String c : resource.getCategories()) {
            if (c.contains("hobson-plugin")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all capabilities published by the core plugin that are associated with the Hobson API.
     *
     * @return an array of Capability objects (or null if a bundle lookup failure occurred)
     */
    protected Capability[] getAPICapabilities() {
        Bundle coreBundle = FrameworkUtil.getBundle(getClass());
        if (coreBundle != null) {
            List<Capability> apiCapabilities = new ArrayList<>();
            BundleRevision revision = coreBundle.adapt(BundleRevision.class);
            List<BundleCapability> caps = revision.getDeclaredCapabilities(null);
            for (BundleCapability bc : caps) {
                Object pkgName = bc.getAttributes().get("osgi.wiring.package");
                Object version = bc.getAttributes().get("bundle-version");
                if (pkgName != null && version != null && pkgName.toString().startsWith("com.whizzosoftware.hobson.api")) {
                    apiCapabilities.add(new HobsonApiCapability(pkgName.toString(), version.toString()));
                }
            }
            return apiCapabilities.toArray(new Capability[apiCapabilities.size()]);
        }
        return null;
    }

    /**
     * Returns the first resource requirement associated with the Hobson API.
     *
     * @param resource the resource to analyze
     *
     * @return a Requirement instance (or null if the resource has no dependency on the Hobson API)
     */
    protected Requirement getAPIRequirement(Resource resource) {
        // Note: This code grabs the first Hobson API package requirement.
        // The assumption being made here is that a plugin will never require different versions for
        // different packages within the Hobson API. While this is almost certainly a safe assumption,
        // it seemed worth making a note of :-)
        for (Requirement req : resource.getRequirements()) {
            if (req.getFilter().contains("(&(package=com.whizzosoftware.hobson.api")) {
                return req;
            }
        }
        return null;
    }

    protected boolean isSatisfied(Requirement requirement, Capability[] apiCapabilities) throws InvalidSyntaxException {
        if (requirement != null && apiCapabilities != null) {
            for (Capability c : apiCapabilities) {
                if (requirement.isSatisfied(c)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
