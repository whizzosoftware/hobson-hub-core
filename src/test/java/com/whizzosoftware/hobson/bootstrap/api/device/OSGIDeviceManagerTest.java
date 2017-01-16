package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.event.device.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.MockPluginManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.bootstrap.api.device.store.MapDBDeviceStore;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class OSGIDeviceManagerTest {
    @Test
    public void testPublishDevice() throws Exception {
        final File tmpFile = File.createTempFile("foo", "db");
        tmpFile.deleteOnExit();
        final MockEventManager em = new MockEventManager();
        final MockPluginManager pm = new MockPluginManager();
        final MapDBDeviceStore ds = new MapDBDeviceStore(tmpFile);
        final OSGIDeviceManager dm = new OSGIDeviceManager();
        dm.setDeviceStore(ds);
        dm.setPluginManager(pm);
        dm.setEventManager(em);
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1", "1.0.0", "");
        plugin.setDeviceManager(dm);
        pm.addLocalPlugin(plugin);
        final MockDeviceProxy proxy = new MockDeviceProxy(plugin, "device1", DeviceType.LIGHTBULB, "name");
        assertFalse(em.hasEvents());
        Future f = dm.publishDevice(proxy, (PropertyContainer)null, null).await();
        f.sync();
        assertTrue(f.isSuccess());

        // make sure onStartup was called
        assertTrue(proxy.isStarted());

        // make sure device's availability was not set
        assertFalse(dm.isDeviceAvailable(DeviceContext.create(plugin.getContext(), proxy.getContext().getDeviceId())));

        // make sure a device startup event was sent
        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof DeviceStartedEvent);
    }

    @Test
    public void testIsDeviceAvailable() throws Exception {
        final long now = System.currentTimeMillis();
        final MockPluginManager pm = new MockPluginManager();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1", "1.0.0", "");
        pm.addLocalPlugin(plugin);
        final OSGIDeviceManager m = new OSGIDeviceManager();
        m.setPluginManager(pm);

        DeviceContext dctx = DeviceContext.create(plugin.getContext(), "device1");
        pm.setLastCheckin(dctx, now);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as unavailable
        pm.setLastCheckin(dctx, null);
        assertFalse(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as available again
        pm.setLastCheckin(dctx, now);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL));
    }
}
