package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MapDBDeviceStoreTest {
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
