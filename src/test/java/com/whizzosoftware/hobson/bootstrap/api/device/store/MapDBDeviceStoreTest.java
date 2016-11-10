package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.action.ActionClass;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

        MapDBDeviceStore store = new MapDBDeviceStore(dbFile);
        HobsonDeviceDescriptor device = new HobsonDeviceDescriptor.Builder(dctx).
            name("Test").
            type(DeviceType.LIGHTBULB).
            modelName("Model").
            actionClasses(ac).
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
}
