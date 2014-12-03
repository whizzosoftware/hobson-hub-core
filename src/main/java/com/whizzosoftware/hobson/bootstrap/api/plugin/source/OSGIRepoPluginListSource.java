/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.PluginType;
import com.whizzosoftware.hobson.api.util.VersionUtil;
import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.HashMap;
import java.util.Map;

/**
 * A PluginListSource implementation that returns plugin information about bundles available in the OBR repository
 * through the RepositoryAdmin service.
 *
 * @author Dan Noguerol
 */
public class OSGIRepoPluginListSource implements PluginListSource {
    private BundleContext bundleContext;

    public OSGIRepoPluginListSource(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
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

    protected Map<String,PluginDescriptor> getPlugins(Resource[] resources) {
        Map<String,PluginDescriptor> resultMap = new HashMap<String,PluginDescriptor>();

        for (Resource repoResource : resources) {
            String sname = repoResource.getSymbolicName();

            PluginType pluginType = PluginType.FRAMEWORK;
            if (isCorePlugin(repoResource)) {
                pluginType = PluginType.CORE;
            } else if (isPlugin(repoResource)) {
                pluginType = PluginType.PLUGIN;
            }

            // TODO: check result map for existing version and compare before replacing...
            PluginDescriptor oldPd = resultMap.get(sname);
            if (oldPd == null || VersionUtil.versionCompare(oldPd.getLatestVersionString(), repoResource.getVersion().toString()) < 0) {
                PluginDescriptor pd = new PluginDescriptor(sname, repoResource.getPresentationName(), buildRepoDescription(repoResource), pluginType, new PluginStatus(PluginStatus.Status.NOT_INSTALLED, null), null);
                pd.setLatestVersionString(repoResource.getVersion().toString());
                resultMap.put(sname, pd);
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
}
