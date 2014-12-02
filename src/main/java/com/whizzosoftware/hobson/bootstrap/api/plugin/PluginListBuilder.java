/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginList;
import com.whizzosoftware.hobson.api.util.VersionUtil;

import java.util.Map;

/**
 * A class that builds a list of plugins from a combination of both a local and repository source. The local source
 * is used for what plugins are currently installed and the repository source is used to determine what non-installed
 * plugins are available as well as what installed plugins have updates available.
 *
 * @author Dan Noguerol
 */
public class PluginListBuilder {
    private com.whizzosoftware.hobson.bootstrap.api.plugin.source.PluginListSource localSource;
    private com.whizzosoftware.hobson.bootstrap.api.plugin.source.PluginListSource repoSource;

    public PluginListBuilder(com.whizzosoftware.hobson.bootstrap.api.plugin.source.PluginListSource localSource, com.whizzosoftware.hobson.bootstrap.api.plugin.source.PluginListSource repoSource) {
        this.localSource = localSource;
        this.repoSource = repoSource;
    }

    public PluginList createPluginList() {
        // get the local list of plugins
        Map<String,PluginDescriptor> pluginMap = localSource.getPlugins();

        // if needed, try to determine latest version of each bundle that is currently in the repository
        if (repoSource != null) {
            for (PluginDescriptor repoResource : repoSource.getPlugins().values()) {
                PluginDescriptor pd = pluginMap.get(repoResource.getId());
                if (pd != null) {
                    String currentVersion = pd.getCurrentVersionString();
                    String latestVersion = repoResource.getLatestVersionString();
                    if (isRepoVersionNewer(currentVersion, latestVersion)) {
                        pd.setLatestVersionString(latestVersion);
                    }
                    // add the description from the repo if the plugin descriptor doesn't already have one
                    if (!pd.hasDescription()) {
                        pd.setDescription(repoResource.getDescription());
                    }
                } else {
                    pluginMap.put(repoResource.getId(), repoResource);
                }
            }
        }

        return new PluginList(pluginMap.values());
    }

    protected boolean isRepoVersionNewer(String myVersion, String repoVersion) {
        return (VersionUtil.versionCompare(myVersion, repoVersion) < 0);
    }
}
