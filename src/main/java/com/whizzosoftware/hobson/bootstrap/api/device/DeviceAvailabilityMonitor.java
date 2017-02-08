/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.event.device.DeviceUnavailableEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitors all devices and posts DeviceUnavailableEvents when any become unavailable.
 *
 * @author Dan Noguerol
 */
public class DeviceAvailabilityMonitor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DeviceAvailabilityMonitor.class);

    private HubContext hubContext;
    private DeviceManager deviceManager;
    private EventManager eventManager;
    private Map<DeviceContext,Long> lastNotificationTimeMap = new HashMap<>();

    DeviceAvailabilityMonitor(HubContext hubContext, DeviceManager deviceManager, EventManager eventManager) {
        this.hubContext = hubContext;
        this.deviceManager = deviceManager;
        this.eventManager = eventManager;
    }

    void setLastNotificationTime(DeviceContext dctx, Long time) {
        lastNotificationTimeMap.put(dctx, time);
    }

    Long getLastNotificationTime(DeviceContext dctx) {
        return lastNotificationTimeMap.get(dctx);
    }

    public void run() {
        run(System.currentTimeMillis());
    }

    public void run(long now) {
        try {
            for (HobsonDeviceDescriptor device : deviceManager.getDevices(hubContext)) {
                DeviceContext dctx = device.getContext();
                Long lastNotificationTime = lastNotificationTimeMap.get(dctx);
                try {
                    Long lastCheckIn = deviceManager.getDeviceLastCheckin(dctx);
                    if (lastCheckIn != null && now - lastCheckIn >= HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL && (lastNotificationTime == null || lastNotificationTime < lastCheckIn)) {
                        eventManager.postEvent(hubContext, new DeviceUnavailableEvent(now, dctx));
                        lastNotificationTimeMap.put(dctx, now);
                    }
                } catch (HobsonNotFoundException ignored) {
                    logger.warn("Found a device descriptor that can't be mapped to a device in the system: {}", dctx);
                }
            }
        } catch (Throwable t) {
            logger.error("Error running device availability monitor", t);
        }
    }
}
