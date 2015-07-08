/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import org.osgi.framework.ServiceRegistration;

/**
 * Encapsulates information about an OSGi variable registration.
 *
 * @author Dan Noguerol
 */
public class VariableRegistration {
    private String pluginId;
    private String deviceId;
    private String name;
    private ServiceRegistration registration;

    public VariableRegistration(String pluginId, String deviceId, String name, ServiceRegistration registration) {
        this.pluginId = pluginId;
        this.deviceId = deviceId;
        this.name = name;
        this.registration = registration;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public void unregister() {
        registration.unregister();
    }
}
