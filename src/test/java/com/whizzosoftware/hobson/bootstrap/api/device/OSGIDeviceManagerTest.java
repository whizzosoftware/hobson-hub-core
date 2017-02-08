package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonInvalidRequestException;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.event.device.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.plugin.MockHobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.MockPluginManager;
import com.whizzosoftware.hobson.api.property.PropertyConstraintType;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.api.config.MapDBConfigurationManager;
import com.whizzosoftware.hobson.bootstrap.api.device.store.MapDBDeviceStore;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        Future f = dm.publishDevice(proxy, null, null).await();
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

    @Test
    public void testSetDeviceConfiguration() throws Exception {
        File file = File.createTempFile("devices", ".db");
        file.deleteOnExit();
        File file2 = File.createTempFile("config", ".db");
        file2.deleteOnExit();

        MockEventManager em = new MockEventManager();

        MapDBDeviceStore store = new MapDBDeviceStore(file);

        MapDBConfigurationManager cm = new MapDBConfigurationManager(file2);
        cm.start();

        MockPluginManager pm = new MockPluginManager();
        MockHobsonPlugin plugin = new MockHobsonPlugin("plugin", "1.0", "description");
        pm.addLocalPlugin(plugin);

        MockDeviceProxy device = new MockDeviceProxy(plugin, "device", DeviceType.LIGHTBULB, "deviceName") {
            @Override
            protected TypedProperty[] getConfigurationPropertyTypes() {
                return new TypedProperty[] {
                    new TypedProperty.Builder("name", "name", "description", TypedProperty.Type.STRING).constraint(PropertyConstraintType.required, true).build(),
                    new TypedProperty.Builder("description", "description", "description", TypedProperty.Type.STRING).build()
                };
            }
        };

        OSGIDeviceManager dm = new OSGIDeviceManager();
        dm.setConfigManager(cm);
        dm.setPluginManager(pm);
        dm.setEventManager(em);
        dm.setDeviceStore(store);

        dm.publishDevice(device, null, null).sync();

        // save valid config
        Map<String,Object> values = new HashMap<>();
        values.put("name", "foo");
        values.put("description", "bar");
        dm.setDeviceConfiguration(device.getContext(), values);
        PropertyContainer pc = dm.getDeviceConfiguration(device.getContext());
        assertNotNull(pc);
        assertEquals("foo", pc.getStringPropertyValue("name"));
        assertEquals("bar", pc.getStringPropertyValue("description"));

        // save config with missing required field
        values = new HashMap<>();
        values.put("description", "bar");
        try {
            dm.setDeviceConfiguration(device.getContext(), values);
            fail("Should have thrown exception");
        } catch (HobsonInvalidRequestException ignored) {}

        // save config with extraneous fields
        values = new HashMap<>();
        values.put("name", "foo");
        values.put("description", "bar");
        values.put("foo", "bar");
        try {
            dm.setDeviceConfiguration(device.getContext(), values);
            fail("Should have thrown exception");
        } catch (HobsonInvalidRequestException ignored) {}
    }
}
