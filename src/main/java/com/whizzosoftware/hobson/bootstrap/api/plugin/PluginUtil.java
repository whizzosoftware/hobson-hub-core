/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * Helper class for methods involving plugins.
 *
 * @author Dan Noguerol
 */
public class PluginUtil {
    /**
     * Returns the currently registered plugin instance for a specific plugin ID.
     *
     * @param context a Bundle context
     * @param pluginId the plugin ID
     *
     * @return a HobsonPlugin instance (or null if the ID wasn't found)
     */
    static public HobsonPlugin getPlugin(BundleContext context, String pluginId) {
        try {
            if (context != null) {
                ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + HobsonPlugin.class.getName() + ")(pluginId=" + pluginId + "))");
                if (references != null && references.length == 1) {
                    return (HobsonPlugin) context.getService(references[0]);
                } else if (references != null && references.length > 1) {
                    throw new HobsonRuntimeException("Duplicate plugin detected for: " + pluginId);
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving device", e);
        }
        return null;
    }
}
