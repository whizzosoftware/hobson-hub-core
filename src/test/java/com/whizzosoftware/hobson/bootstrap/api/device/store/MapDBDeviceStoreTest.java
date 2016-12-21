package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.action.ActionClass;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MapDBDeviceStoreTest {
    @Test
    public void testSaveDevice() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");

        List<ActionClass> ac = new ArrayList<>();
        ActionClass a = new ActionClass(PropertyContainerClassContext.create(DeviceContext.createLocal("plugin1", "device1"), "ac1"), "name", "description", true, 2000);
        a.addSupportedProperty(new TypedProperty.Builder("prop1", "pname", "pdesc", TypedProperty.Type.STRING).build());
        ac.add(a);

        Set<String> tags = new HashSet<>();
        tags.add("tag1");
        tags.add("tag2");

        MapDBDeviceStore store = new MapDBDeviceStore(dbFile);
        HobsonDeviceDescriptor device = new HobsonDeviceDescriptor.Builder(dctx).
            name("Test").
            type(DeviceType.LIGHTBULB).
            modelName("Model").
            actionClasses(ac).
            tags(tags).
            build();
        store.saveDevice(device);

        device = store.getDevice(dctx);
        assertEquals("Test", device.getName());
        assertEquals(DeviceType.LIGHTBULB, device.getType());
        assertEquals("Model", device.getModelName());

        assertTrue(device.hasActionClasses());
        assertEquals(1, device.getActionClasses().size());

        a = device.getActionClasses().iterator().next();
        assertEquals("plugin1", a.getContext().getPluginId());
        assertEquals("device1", a.getContext().getDeviceId());
        assertEquals("ac1", a.getContext().getContainerClassId());
        assertEquals("name", a.getName());
        assertEquals("description", a.getDescription());
        assertTrue(a.isTaskAction());
        assertEquals(2000, a.getTimeoutInterval());
        assertTrue(a.hasSupportedProperties());
        assertEquals(1, a.getSupportedProperties().size());
        TypedProperty tp = a.getSupportedProperties().get(0);
        assertEquals("prop1", tp.getId());
        assertEquals("pname", tp.getName());
        assertEquals("pdesc", tp.getDescription());
        assertEquals(TypedProperty.Type.STRING, tp.getType());

        assertNotNull(device.getTags());
        Iterator it = device.getTags().iterator();
        assertEquals("tag1", it.next());
        assertEquals("tag2", it.next());
    }

    @Test
    public void testSetDeviceName() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");

        MapDBDeviceStore store = new MapDBDeviceStore(dbFile);
        HobsonDeviceDescriptor device = new HobsonDeviceDescriptor.Builder(dctx).name("Test").type(DeviceType.LIGHTBULB).modelName("Model").build();
        store.saveDevice(device);

        device = store.getDevice(dctx);
        assertNotNull(device);
        assertEquals("Test", device.getName());
        assertEquals(DeviceType.LIGHTBULB, device.getType());
        assertEquals("Model", device.getModelName());

        store.setDeviceName(dctx, "Test2");

        device = store.getDevice(dctx);
        assertEquals("Test2", device.getName());
        assertEquals(DeviceType.LIGHTBULB, device.getType());
        assertEquals("Model", device.getModelName());
    }

    @Test
    public void testSetDeviceTags() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");

        MapDBDeviceStore store = new MapDBDeviceStore(dbFile);
        HobsonDeviceDescriptor device = new HobsonDeviceDescriptor.Builder(dctx).name("Test").type(DeviceType.LIGHTBULB).modelName("Model").build();
        store.saveDevice(device);

        device = store.getDevice(dctx);
        assertNotNull(device);
        assertNull(device.getTags());

        Set<String> tags = new HashSet<>();
        tags.add("tag1");
        tags.add("tag2");
        store.setDeviceTags(dctx, tags);

        device = store.getDevice(dctx);
        assertNotNull(device);
        assertNotNull(device.getTags());
        assertEquals(2, device.getTags().size());
        Iterator it = device.getTags().iterator();
        assertEquals("tag1", it.next());
        assertEquals("tag2", it.next());

        tags = store.getDeviceTags(dctx);
        assertNotNull(tags);
        it = tags.iterator();
        assertEquals("tag1", it.next());
        assertEquals("tag2", it.next());
    }
}
