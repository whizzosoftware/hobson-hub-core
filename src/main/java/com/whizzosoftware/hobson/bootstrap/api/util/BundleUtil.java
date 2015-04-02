/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.util;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * A convenience class for performing various bundle related functions.
 *
 * @author Dan Noguerol
 */
public class BundleUtil {
    static public BundleContext getBundleContext(Class clazz, String bundleSymbolicName) {
        if (bundleSymbolicName == null) {
            return FrameworkUtil.getBundle(clazz).getBundleContext();
        } else {
            for (Bundle bundle : FrameworkUtil.getBundle(clazz).getBundleContext().getBundles()) {
                if (bundleSymbolicName.equalsIgnoreCase(bundle.getSymbolicName())) {
                    return bundle.getBundleContext();
                }
            }
        }
        throw new HobsonNotFoundException("Unable to find bundle with name: " + bundleSymbolicName);
    }

    /**
     * Retrieve the bundle for a specific symbolic name.
     *
     * @param symbolicName the symbolic name
     *
     * @return a Bundle instance (or null if not found)
     */
    static public Bundle getBundleForSymbolicName(String symbolicName) {
        // TODO: is there a better way to do this?
        for (Bundle bundle : FrameworkUtil.getBundle(BundleUtil.class).getBundleContext().getBundles()) {
            if (symbolicName.equalsIgnoreCase(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * Create a PluginStatus instance from an OSGi bundle state.
     *
     * @param state the bundle state
     *
     * @return a PluginStatus instance
     */
    static public PluginStatus createPluginStatusFromBundleState(int state) {
        switch (state) {
            case Bundle.ACTIVE:
                return PluginStatus.running();
            case Bundle.INSTALLED:
            case Bundle.RESOLVED:
                return PluginStatus.stopped();
            default:
                return PluginStatus.notInstalled();
        }
    }
}
