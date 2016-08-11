package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.device.proxy.DeviceProxy;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.MockPluginManager;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import static org.junit.Assert.*;

public class OSGIDeviceManagerTest {
    @Test
    public void testPublishDevice() throws Exception {
        final MockEventManager em = new MockEventManager();
        final MockPluginManager pm = new MockPluginManager();
        final OSGIDeviceManager dm = new OSGIDeviceManager();
        dm.setPluginManager(pm);
        dm.setEventManager(em);
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1");
        plugin.setDeviceManager(dm);
        pm.addLocalPlugin(plugin);
        final MockDeviceProxy proxy = new MockDeviceProxy(plugin, "device1", DeviceType.LIGHTBULB);
        assertFalse(em.hasEvents());
        Future f = dm.publishDevice(plugin.getContext(), proxy, null, System.currentTimeMillis()).await();
        assertTrue(f.isSuccess());

        // make sure onStartup was called
        assertTrue(proxy.isStarted());

        // make sure device's availability was not set
        assertFalse(dm.isDeviceAvailable(DeviceContext.create(plugin.getContext(), proxy.getDeviceId())));

        // make sure a device startup event was sent
        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceStartedEvent);
    }

    @Test
    public void testIsDeviceAvailable() throws Exception {
        final long now = System.currentTimeMillis();
        final MockPluginManager pm = new MockPluginManager();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1");
        pm.addLocalPlugin(plugin);
        final OSGIDeviceManager m = new OSGIDeviceManager();
        m.setPluginManager(pm);
        final MockDeviceProxy proxy = new MockDeviceProxy(plugin, "device1", DeviceType.LIGHTBULB);

        // publish device
        Future f = m.publishDevice(plugin.getContext(), proxy, null, now).await();
        assertTrue(f.isSuccess());
        DeviceContext dctx = DeviceContext.create(plugin.getContext(), proxy.getDeviceId());
        m.setDeviceAvailability(dctx, true, now);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as unavailable
        m.setDeviceAvailability(dctx, false, null);
        assertFalse(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as available again
        m.setDeviceAvailability(dctx, true, null);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL));
    }
}
