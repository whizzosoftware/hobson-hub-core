/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.store;

import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;

/**
 * An interface for saving and retrieving plugin configuration data.
 *
 * @author Dan Noguerol
 */
public interface PluginConfigurationStore {
    PropertyContainer getLocalPluginConfiguration(PluginContext ctx, PropertyContainerClass configurationClass);
    void setLocalPluginConfiguration(PluginContext ctx, PropertyContainerClass configurationClass, PropertyContainer newConfig);
    void setLocalPluginConfigurationProperty(PluginContext ctx, PropertyContainerClass configurationClass, String name, Object value);
    void close();
}
