/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;

import java.util.Map;

interface DeviceConfigurationStore {
    PropertyContainer getDeviceConfiguration(DeviceContext ctx, PropertyContainerClass metas, String name);
    Object getDeviceConfigurationProperty(DeviceContext ctx, String name);
    void setDeviceConfigurationProperties(DeviceContext ctx, PropertyContainerClass configurationClass, String deviceName, Map<String, Object> values, boolean overwrite);
    void close();
}
