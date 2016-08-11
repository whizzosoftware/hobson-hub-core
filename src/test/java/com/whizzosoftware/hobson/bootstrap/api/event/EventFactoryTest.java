/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.DeviceUnavailableEvent;
import com.whizzosoftware.hobson.api.event.DeviceVariableUpdateEvent;
import com.whizzosoftware.hobson.api.event.HobsonEvent;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableUpdate;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventFactoryTest {
    @Test
    public void testVariableUpdateNotificationEventWithNoUpdates() throws Exception {
        EventFactory ef = new EventFactory();
        ef.addEventClass(DeviceVariableUpdateEvent.ID, DeviceVariableUpdateEvent.class);

        Map<String,Object> props = new HashMap<>();
        props.put(HobsonEvent.PROP_EVENT_ID, DeviceVariableUpdateEvent.ID);

        HobsonEvent event = ef.createEvent(props);
        assertTrue(event instanceof DeviceVariableUpdateEvent);
        assertNull(((DeviceVariableUpdateEvent)event).getUpdates());
    }

    @Test
    public void testVariableUpdateNotificationEventWithUpdates() throws Exception {
        EventFactory ef = new EventFactory();
        ef.addEventClass(DeviceVariableUpdateEvent.ID, DeviceVariableUpdateEvent.class);

        Map<String,Object> props = new HashMap<>();
        List<DeviceVariableUpdate> updates = new ArrayList<>();
        updates.add(new DeviceVariableUpdate(DeviceVariableContext.createGlobal(PluginContext.createLocal("plugin"), "name"), null, "value"));
        props.put(HobsonEvent.PROP_EVENT_ID, DeviceVariableUpdateEvent.ID);
        props.put(DeviceVariableUpdateEvent.PROP_UPDATES, updates);

        HobsonEvent event = ef.createEvent(props);
        assertTrue(event instanceof DeviceVariableUpdateEvent);

        DeviceVariableUpdateEvent vune = (DeviceVariableUpdateEvent)event;
        assertNotNull(vune.getUpdates());
        assertEquals(1, vune.getUpdates().size());
        assertEquals("plugin", vune.getUpdates().get(0).getPluginId());
        assertEquals("name", vune.getUpdates().get(0).getName());
        assertEquals("value", vune.getUpdates().get(0).getNewValue());
    }

    @Test
    public void testDeviceUnavailableEvent() throws Exception {
        EventFactory ef = new EventFactory();
        ef.addEventClass(DeviceUnavailableEvent.ID, DeviceUnavailableEvent.class);

        Map<String,Object> props = new HashMap<>();
        props.put(HobsonEvent.PROP_EVENT_ID, DeviceUnavailableEvent.ID);
        props.put(DeviceUnavailableEvent.PROP_DEVICE_CONTEXT, DeviceContext.create(HubContext.createLocal(), "plugin", "device"));

        HobsonEvent event = ef.createEvent(props);
        assertTrue(event instanceof DeviceUnavailableEvent);
        assertEquals("local:plugin:device", ((DeviceUnavailableEvent)event).getDeviceContext().toString());
    }
}
