/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescriptor;

import java.util.Collection;
import java.util.Set;

/**
 * Interface for classes that provide storage of Hobson devices.
 *
 * @author Dan Noguerol
 */
public interface DeviceStore {
    /**
     * Unpublishes a specific device.
     *
     * @param ctx the device context
     */
    void deleteDevice(DeviceContext ctx);

    /**
     * Retrieve a list of all published devices.
     *
     * @param ctx a hub context
     *
     * @return a Collection of HobsonDeviceDescription instances
     */
    Collection<HobsonDeviceDescriptor> getAllDevices(HubContext ctx);

    /**
     * Retrieve a list of all devices published by a specific plugin.
     *
     * @param ctx a plugin context
     *
     * @return a Collection of HobsonDeviceDescription instances
     */
    Collection<HobsonDeviceDescriptor> getAllDevices(PluginContext ctx);

    /**
     * Retrieve a list of all devices with the specified tags. This will return any devices that have all of the
     * tags in the set.
     *
     * @param ctx a hub context
     * @param tag a tag name
     *
     * @return a Collection of DeviceContext instances
     */
    Collection<DeviceContext> getAllDeviceContextsWithTag(HubContext ctx, String tag);

    /**
     * Retrieves a specific device.
     *
     * @param ctx the device context
     *
     * @return a HobsonDeviceDescription instance
     */
    HobsonDeviceDescriptor getDevice(DeviceContext ctx);

    /**
     * Returns a device name.
     *
     * @param ctx the device context
     *
     * @return the device name
     */
    String getDeviceName(DeviceContext ctx);

    /**
     * Returns a device's tags.
     *
     * @param ctx the device context
     *
     * @return the tags associated with the device
     */
    Set<String> getDeviceTags(DeviceContext ctx);

    /**
     * Indicates whether a particular device context has been published.
     *
     * @param ctx a device context
     *
     * @return a boolean
     */
    boolean hasDevice(DeviceContext ctx);

    /**
     * Allows the device store to perform whatever implementation-specific housekeeping tasks are needed.
     */
    void performHousekeeping();

    /**
     * Publishes a new device.
     *
     * @param device the device to publish
     */
    void saveDevice(HobsonDeviceDescriptor device);

    /**
     * Saves a device variable descriptor.
     *
     * @param dvd the device variable descriptor
     */
    void saveDeviceVariable(DeviceVariableDescriptor dvd);

    /**
     * Sets a device's name.
     *
     * @param ctx the device context
     * @param name the new device name
     */
    void setDeviceName(DeviceContext ctx, String name);

    /**
     * Sets a device's tags.
     *
     * @param ctx the device context
     * @param tags the tags
     */
    void setDeviceTags(DeviceContext ctx, Set<String> tags);

    /**
     * Start the device store.
     */
    void start();

    /**
     * Stop the device store.
     */
    void stop();
}
