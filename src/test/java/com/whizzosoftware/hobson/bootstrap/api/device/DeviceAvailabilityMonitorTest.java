/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.device.MockDeviceManager;
import com.whizzosoftware.hobson.api.device.MockHobsonDevice;
import com.whizzosoftware.hobson.api.event.DeviceUnavailableEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceAvailabilityMonitorTest {
    @Test
    public void testRun() {
        HubContext hctx = HubContext.createLocal();
        MockDeviceManager dm = new MockDeviceManager();
        MockEventManager em = new MockEventManager();

        // create some devices
        long now = System.currentTimeMillis();
        MockHobsonPlugin plugin = new MockHobsonPlugin("plugin");
        plugin.setDeviceManager(dm);
        MockHobsonDevice device1  = new MockHobsonDevice(plugin, "device1");
        device1.setDeviceAvailability(true, now);
        dm.publishDevice(device1);
        MockHobsonDevice device2 = new MockHobsonDevice(plugin, "device2");
        device2.setDeviceAvailability(true, now);
        dm.publishDevice(device2);

        DeviceAvailabilityMonitor monitor = new DeviceAvailabilityMonitor(hctx, dm, em);

        // make sure no events occur before the 5 minute mark
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL / 2);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL - 1);
        assertEquals(0, em.getEventCount());

        // make sure two events occur at the 5 minute mark
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL);
        assertEquals(2, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceUnavailableEvent);
        assertTrue("local:plugin:device1".equals(em.getEvent(0).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()) || "local:plugin:device2".equals(em.getEvent(0).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()));
        assertTrue(em.getEvent(1) instanceof DeviceUnavailableEvent);
        assertTrue("local:plugin:device1".equals(em.getEvent(1).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()) || "local:plugin:device2".equals(em.getEvent(1).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()));
        em.clearEvents();

        // make sure no events fire after the 5 minute mark
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL + 1);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL + 100000);
        assertEquals(0, em.getEventCount());

        // check in one device
        device1.setDeviceAvailability(true, now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL + 1000);

        // make sure no events fire after the 5 minute mark
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL + 900);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL * 2 - 1);
        assertEquals(0, em.getEventCount());

        // make sure one event fires after the 5 minute mark
        monitor.run(now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL * 2 + 1000);
        assertEquals(1, em.getEventCount());
    }
}
