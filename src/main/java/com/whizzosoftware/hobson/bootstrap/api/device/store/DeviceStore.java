/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;

import java.util.Collection;
import java.util.Map;

/**
 * Interface for classes that provide storage of Hobson devices.
 *
 * @author Dan Noguerol
 */
public interface DeviceStore {
    /**
     * Start the device store.
     */
    void start();

    /**
     * Stop the device store.
     */
    void stop();

    /**
     * Retrieve a list of all published devices.
     *
     * @param ctx a hub context
     *
     * @return a Collection of HobsonDevice instances
     */
    Collection<HobsonDevice> getAllDevices(HubContext ctx);

    /**
     * Retrieve a list of all devices published by a specific plugin.
     *
     * @param ctx a plugin context
     *
     * @return a Collection of HobsonDevice instances
     */
    Collection<HobsonDevice> getAllDevices(PluginContext ctx);

    /**
     * Indicates whether a particular device context has been published.
     *
     * @param ctx a device context
     *
     * @return a boolean
     */
    boolean hasDevice(DeviceContext ctx);

    /**
     * Retrieves a specific device.
     *
     * @param ctx the device context
     *
     * @return a HobsonDevice instance
     */
    HobsonDevice getDevice(DeviceContext ctx);

    /**
     * Publishes a new device.
     *
     * @param device the device to publish
     * @param republish indicates whether a re-publish should occur if the device has already been published (will fail otherwise)
     */
    void publishDevice(HobsonDevice device, boolean republish);

    /**
     * Unpublishes a specific device.
     *
     * @param ctx the device context
     */
    void unpublishDevice(DeviceContext ctx);
}
