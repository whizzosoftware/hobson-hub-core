/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.device.DeviceAvailableEvent;
import com.whizzosoftware.hobson.api.event.device.DeviceEvent;
import com.whizzosoftware.hobson.api.event.plugin.PluginStatusChangeEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EventHandlerAdapterTest {
    @Test
    public void testHandleEvent() throws Exception {
        final List<PresenceEvent> events = new ArrayList<>();
        final List<Object> events2 = new ArrayList<>();
        final List<Object> events3 = new ArrayList<>();
        final List<Object> events4 = new ArrayList<>();

        EventFactory ef = new EventFactory();
        ef.addEventClass(PresenceUpdateNotificationEvent.ID, PresenceUpdateNotificationEvent.class);
        ef.addEventClass(DeviceAvailableEvent.ID, DeviceAvailableEvent.class);
        ef.addEventClass(PluginStatusChangeEvent.ID, PluginStatusChangeEvent.class);

        EventHandlerAdapter a = new EventHandlerAdapter(ef, new Object() {
            @EventHandler
            public void handle(PresenceEvent e) {
                events.add(e);
            }
            @EventHandler
            public void handle2(DeviceEvent e) {
                events2.add(e);
            }
            @EventHandler
            public void handle3(PluginStatusChangeEvent e) {
                events4.add(e);
            }
            @EventHandler
            public void handle3(HobsonEvent e) {
                events3.add(e);
            }
        }, new EventCallbackInvoker() {
            @Override
            public void invoke(Method m, Object o, HobsonEvent e) {
                try {
                    m.invoke(o, e);
                } catch (Throwable t) {
                    t.printStackTrace();
                    fail();
                }
            }
        });

        assertEquals(0, events.size());
        assertEquals(0, events2.size());
        assertEquals(0, events3.size());
        assertEquals(0, events4.size());
        a.handleEvent(new Event("topic", new PresenceUpdateNotificationEvent(System.currentTimeMillis(), null, null, null).getProperties()));
        assertEquals(1, events.size());
        assertEquals(0, events2.size());
        assertEquals(1, events3.size());
        assertEquals(0, events4.size());
        a.handleEvent(new Event("topic", new DeviceAvailableEvent(System.currentTimeMillis(), DeviceContext.create(HubContext.createLocal(), "plugin1", "device1")).getProperties()));
        assertEquals(1, events.size());
        assertEquals(1, events2.size());
        assertEquals(2, events3.size());
        assertEquals(0, events4.size());
        a.handleEvent(new Event("topic", new PluginStatusChangeEvent(System.currentTimeMillis(), PluginContext.createLocal("plugin1"), PluginStatus.running()).getProperties()));
        assertEquals(1, events.size());
        assertEquals(1, events2.size());
        assertEquals(3, events3.size());
        assertEquals(1, events4.size());
    }
}
