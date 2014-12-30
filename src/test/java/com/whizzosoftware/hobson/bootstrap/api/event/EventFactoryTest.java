/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.event.HobsonEvent;
import com.whizzosoftware.hobson.api.event.VariableUpdateNotificationEvent;

import com.whizzosoftware.hobson.api.variable.VariableUpdate;
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
        ef.addEventClass(VariableUpdateNotificationEvent.ID, VariableUpdateNotificationEvent.class);

        Map<String,Object> props = new HashMap<>();
        props.put(HobsonEvent.PROP_EVENT_ID, VariableUpdateNotificationEvent.ID);

        HobsonEvent event = ef.createEvent(props);
        assertTrue(event instanceof VariableUpdateNotificationEvent);
        assertNull(((VariableUpdateNotificationEvent)event).getUpdates());
    }

    @Test
    public void testVariableUpdateNotificationEventWithUpdates() throws Exception {
        EventFactory ef = new EventFactory();
        ef.addEventClass(VariableUpdateNotificationEvent.ID, VariableUpdateNotificationEvent.class);

        Map<String,Object> props = new HashMap<>();
        List<VariableUpdate> updates = new ArrayList<>();
        updates.add(new VariableUpdate("plugin", "name", "value"));
        props.put(HobsonEvent.PROP_EVENT_ID, VariableUpdateNotificationEvent.ID);
        props.put(VariableUpdateNotificationEvent.PROP_UPDATES, updates);

        HobsonEvent event = ef.createEvent(props);
        assertTrue(event instanceof VariableUpdateNotificationEvent);

        VariableUpdateNotificationEvent vune = (VariableUpdateNotificationEvent)event;
        assertNotNull(vune.getUpdates());
        assertEquals(1, vune.getUpdates().size());
        assertEquals("plugin", vune.getUpdates().get(0).getPluginId());
        assertEquals("name", vune.getUpdates().get(0).getName());
        assertEquals("value", vune.getUpdates().get(0).getValue());
    }
}
