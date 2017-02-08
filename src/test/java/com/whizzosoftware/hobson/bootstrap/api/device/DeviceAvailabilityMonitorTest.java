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
import com.whizzosoftware.hobson.api.event.device.DeviceAvailableEvent;
import com.whizzosoftware.hobson.api.event.device.DeviceUnavailableEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import io.netty.util.concurrent.Future;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceAvailabilityMonitorTest {
    @Test
    public void testRun() throws Exception {
        final HubContext hctx = HubContext.createLocal();
        final MockDeviceManager dm = new MockDeviceManager();
        final MockEventManager em = new MockEventManager();

        // create some devices
        final long now = System.currentTimeMillis();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin", "1.0.0", "");
        plugin.setDeviceManager(dm);
        plugin.setEventManager(em);
        final MockDeviceProxy device1  = new MockDeviceProxy(plugin, "device1", DeviceType.LIGHTBULB);
        Future f = dm.publishDevice(device1, null, null).await();
        assertTrue(f.isSuccess());
        device1.setLastCheckin(now);

        final MockDeviceProxy device2 = new MockDeviceProxy(plugin, "device2", DeviceType.LIGHTBULB);
        f = dm.publishDevice(device2, null, null).await();
        assertTrue(f.isSuccess());
        device2.setLastCheckin(now);

        // confirm initial availability events
        assertEquals(2, em.getEventCount());
        em.clearEvents();

        DeviceAvailabilityMonitor monitor = new DeviceAvailabilityMonitor(hctx, dm, em);

        // make sure no events occur before the 10 minute mark
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL / 2);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL - 1);
        assertEquals(0, em.getEventCount());

        // make sure two events occur at the 10 minute mark
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL);
        assertEquals(2, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceUnavailableEvent);
        assertTrue("local:plugin:device1".equals(em.getEvent(0).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()) || "local:plugin:device2".equals(em.getEvent(0).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()));
        assertTrue(em.getEvent(1) instanceof DeviceUnavailableEvent);
        assertTrue("local:plugin:device1".equals(em.getEvent(1).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()) || "local:plugin:device2".equals(em.getEvent(1).getProperties().get(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT).toString()));
        em.clearEvents();

        // make sure no events fire after the 10 minute mark
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL + 1);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL + 100000);
        assertEquals(0, em.getEventCount());

        // check in one device
        device1.setLastCheckin(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL + 1000);
        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceAvailableEvent);
        em.clearEvents();

        // make sure no events fire after the 10 minute mark
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL + 900);
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL * 2 - 1);
        assertEquals(0, em.getEventCount());

        // make sure one event fires after the 10 minute mark
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL * 2 + 1000);
        assertEquals(1, em.getEventCount());
    }

    @Test
    public void testLastUpdate() throws Exception {
        final HubContext hctx = HubContext.createLocal();
        final MockDeviceManager dm = new MockDeviceManager();
        final MockEventManager em = new MockEventManager();
        final DeviceAvailabilityMonitor monitor = new DeviceAvailabilityMonitor(hctx, dm, em);

        final long now = System.currentTimeMillis();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin", "1.0.0", "");
        plugin.setDeviceManager(dm);
        plugin.setEventManager(em);

        // create a device
        final MockDeviceProxy device1  = new MockDeviceProxy(plugin, "device1", DeviceType.LIGHTBULB);
        Future f = dm.publishDevice(device1, null, null).await();
        assertTrue(f.isSuccess());

        // now == last checkin with no last notification
        device1.setLastCheckin(now);
        em.clearEvents();
        assertEquals(0, em.getEventCount());
        monitor.run(now);
        assertEquals(0, em.getEventCount());

        // timeout interval < last checkin < now with no last notification
        em.clearEvents();
        assertEquals(0, em.getEventCount());
        monitor.run(now + 1);
        assertEquals(0, em.getEventCount());

        // timeout interval = last checkin < now
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL);
        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceUnavailableEvent);
        assertEquals(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL, (long)monitor.getLastNotificationTime(device1.getContext()));

        // last checkin < timeout interval < now with last notify > last checkin
        device1.setLastCheckin(now);
        monitor.setLastNotificationTime(device1.getContext(), now + 1);
        em.clearEvents();
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL);
        assertEquals(0, em.getEventCount());

        // last checkin < timeout interval < now with last notify < last checkin
        device1.setLastCheckin(now);
        monitor.setLastNotificationTime(device1.getContext(), now - 1);
        em.clearEvents();
        assertEquals(0, em.getEventCount());
        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL);
        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceUnavailableEvent);
    }

    @Test
    public void testRunWithOneMissingDevice() throws Exception {
        final HubContext hctx = HubContext.createLocal();
        final MockDeviceManager dm = new MockDeviceManager() {
            @Override
            public Long getDeviceLastCheckin(DeviceContext dctx) {
                if ("device1".equals(dctx.getDeviceId())) {
                    throw new HobsonNotFoundException("Device not found");
                } else {
                    return super.getDeviceLastCheckin(dctx);
                }
            }
        };
        final MockEventManager em = new MockEventManager();
        final DeviceAvailabilityMonitor monitor = new DeviceAvailabilityMonitor(hctx, dm, em);

        long now = System.currentTimeMillis();

        MockHobsonPlugin plugin = new MockHobsonPlugin("plugin", "version", "description");
        plugin.setEventManager(em);
        MockDeviceProxy device1 = new MockDeviceProxy(plugin, "device1", DeviceType.SENSOR);
        device1.setLastCheckin(now);
        MockDeviceProxy device2 = new MockDeviceProxy(plugin, "device2", DeviceType.LIGHTBULB);
        device2.setLastCheckin(now);
        dm.publishDevice(device1, null, null).await();
        dm.publishDevice(device2, null, null).await();

        em.clearEvents();
        assertEquals(0, em.getEventCount());

        monitor.run(now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL + 1);

        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceUnavailableEvent);
        assertEquals(((DeviceUnavailableEvent)em.getEvent(0)).getDeviceContext(), device2.getContext());
    }
}
