/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.VariableContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;

import java.util.Collection;

/**
 * An interface for variable storage.
 *
 * @author Dan Noguerol
 */
public interface VariableStore {
    /**
     * Return all variables published to the hub.
     *
     * @param ctx the hub context
     *
     * @return a Collection of HobsonVariable instances
     */
    Collection<HobsonVariable> getAllVariables(HubContext ctx);

    /**
     * Return all variables published by a device.
     *
     * @param ctx the device context
     *
     * @return a Collection of HobsonVariable instances
     */
    Collection<HobsonVariable> getDeviceVariables(DeviceContext ctx);

    /**
     * Return all global variables published to the hub.
     *
     * @param ctx the hub context
     *
     * @return a Collection of HobsonVariable instances
     */
    Collection<HobsonVariable> getAllGlobalVariables(HubContext ctx);

    /**
     * Return all global variables published by a plugin.
     *
     * @param ctx the plugin context
     *
     * @return a Collection of HobsonVariable instances
     */
    Collection<HobsonVariable> getPluginGlobalVariables(PluginContext ctx);

    /**
     * Indicates whether a variable has been published.
     *
     * @param ctx the variable context
     *
     * @return a boolean
     */
    boolean hasVariable(VariableContext ctx);

    /**
     * Returns a variable.
     *
     * @param ctx the variable context
     *
     * @return a HobsonVariable instance
     * @throws com.whizzosoftware.hobson.api.variable.VariableNotFoundException if the variable does not exist
     */
    HobsonVariable getVariable(VariableContext ctx);

    /**
     * Returns a unique list of all the variable names that have been published.
     *
     * @return a Collection of names
     */
    Collection<String> getVariableNames();

    /**
     * Published a variable.
     *
     * @param variable the variable to publish
     */
    void publishVariable(HobsonVariable variable);

    /**
     * Un-publishes a variable.
     *
     * @param ctx a variable context
     */
    void unpublishVariable(VariableContext ctx);

    /**
     * Un-publishes all variables published by a device.
     *
     * @param ctx the device context
     */
    void unpublishVariables(DeviceContext ctx);
}
