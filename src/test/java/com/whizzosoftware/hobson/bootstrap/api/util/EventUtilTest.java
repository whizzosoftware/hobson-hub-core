package com.whizzosoftware.hobson.bootstrap.api.util;

import com.whizzosoftware.hobson.api.config.Configuration;
import com.whizzosoftware.hobson.api.device.AbstractHobsonDevice;
import com.whizzosoftware.hobson.api.device.DeviceType;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.DeviceStoppedEvent;
import com.whizzosoftware.hobson.api.event.EventTopics;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class EventUtilTest {
    @Test
    public void testDeviceStartedToEventMapping() {
        MockHobsonDevice device = new MockHobsonDevice(null);
        DeviceStartedEvent dse = new DeviceStartedEvent(device);
        Event e = EventUtil.createEventFromHobsonEvent(dse);

        assertEquals(dse.getTopic(), e.getTopic());
        assertEquals(dse.getEventId(), e.getProperty("eventId"));
        assertEquals(dse.getDevice(), e.getProperty(DeviceStartedEvent.PROP_DEVICE));
    }

    @Test
    public void testEventToDeviceStartedMapping() {
        MockHobsonDevice device = new MockHobsonDevice(null);

        Map props = new HashMap();
        props.put(DeviceStartedEvent.PROP_EVENT_ID, DeviceStartedEvent.ID);
        props.put(DeviceStartedEvent.PROP_DEVICE, device);
        Event e = new Event(EventTopics.DEVICES_TOPIC, props);

        DeviceStartedEvent dse = new DeviceStartedEvent(EventUtil.createMapFromEvent(e));

        assertNull(dse.getPluginId());
        assertEquals(DeviceStartedEvent.ID, dse.getEventId());
        assertEquals(EventTopics.DEVICES_TOPIC, dse.getTopic());
        assertEquals(device, dse.getDevice());
    }

    @Test
    public void testDeviceStoppedEventMapping() {
        MockHobsonDevice device = new MockHobsonDevice(null);
        DeviceStoppedEvent dse = new DeviceStoppedEvent(device);
        Event e = EventUtil.createEventFromHobsonEvent(dse);

        assertEquals(dse.getTopic(), e.getTopic());
        assertEquals(dse.getEventId(), e.getProperty("eventId"));
        assertEquals(dse.getDevice(), e.getProperty(DeviceStoppedEvent.PROP_DEVICE));
    }

    @Test
    public void testEventToDeviceStoppedMapping() {
        MockHobsonDevice device = new MockHobsonDevice(null);

        Map props = new HashMap();
        props.put(DeviceStoppedEvent.PROP_EVENT_ID, DeviceStoppedEvent.ID);
        props.put(DeviceStoppedEvent.PROP_DEVICE, device);
        Event e = new Event(EventTopics.DEVICES_TOPIC, props);

        DeviceStoppedEvent dse = new DeviceStoppedEvent(EventUtil.createMapFromEvent(e));

        assertNull(dse.getPluginId());
        assertEquals(DeviceStoppedEvent.ID, dse.getEventId());
        assertEquals(EventTopics.DEVICES_TOPIC, dse.getTopic());
        assertEquals(device, dse.getDevice());
    }

    private class MockHobsonDevice extends AbstractHobsonDevice {

        /**
         * Constructor.
         *
         * @param plugin the HobsonPlugin that created this device
         */
        public MockHobsonDevice(HobsonPlugin plugin) {
            super(plugin, "bulb");
        }

        @Override
        public DeviceType getType() {
            return DeviceType.LIGHTBULB;
        }

        @Override
        public void onStartup(Configuration config) {

        }

        @Override
        public void onShutdown() {

        }

        @Override
        public void onSetVariable(String variableName, Object value) {

        }
    }
}
