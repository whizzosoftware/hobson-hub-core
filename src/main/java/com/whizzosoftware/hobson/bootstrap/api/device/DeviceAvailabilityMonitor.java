/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.event.DeviceUnavailableEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitors all devices and posts DeviceUnavailableEvents when any become unavailable.
 *
 * @author Dan Noguerol
 */
public class DeviceAvailabilityMonitor implements Runnable {
    private HubContext hubContext;
    private DeviceManager deviceManager;
    private EventManager eventManager;
    private Map<DeviceContext,Long> lastNotificationTimeMap = new HashMap<>();

    public DeviceAvailabilityMonitor(HubContext hubContext, DeviceManager deviceManager, EventManager eventManager) {
        this.hubContext = hubContext;
        this.deviceManager = deviceManager;
        this.eventManager = eventManager;
    }

    public void run() {
        run(System.currentTimeMillis());
    }

    public void run(long now) {
        for (HobsonDevice device : deviceManager.getAllDevices(hubContext)) {
            Long lastNotificationTime = lastNotificationTimeMap.get(device.getContext());
            Long lastCheckIn = deviceManager.getDeviceLastCheckIn(device.getContext());
            if (lastCheckIn != null && now - lastCheckIn >= HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL && (lastNotificationTime == null || lastNotificationTime < lastCheckIn)) {
                eventManager.postEvent(hubContext, new DeviceUnavailableEvent(now, device.getContext()));
                lastNotificationTimeMap.put(device.getContext(), now);
            }
        }
    }
}
