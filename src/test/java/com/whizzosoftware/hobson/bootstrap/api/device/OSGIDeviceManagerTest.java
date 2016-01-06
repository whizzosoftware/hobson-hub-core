package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.device.HobsonDeviceStub;
import org.junit.Test;
import static org.junit.Assert.*;

public class OSGIDeviceManagerTest {
    @Test
    public void testIsDeviceAvailable() {
        long now = System.currentTimeMillis();
        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");
        OSGIDeviceManager m = new OSGIDeviceManager();

        // publish device
        m.publishDevice(new HobsonDeviceStub.Builder(dctx).build(), false, now);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as unavailable
        m.setDeviceAvailability(dctx, false, null);
        assertFalse(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL));

        // explicitly set as available again
        m.setDeviceAvailability(dctx, true, null);
        assertTrue(m.isDeviceAvailable(dctx, now));
        assertFalse(m.isDeviceAvailable(dctx, now + HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL));
    }
}
