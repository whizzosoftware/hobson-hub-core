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

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.device.DeviceEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateNotificationEvent;
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

        EventFactory ef = new EventFactory();
        ef.addEventClass(PresenceUpdateNotificationEvent.ID, PresenceUpdateNotificationEvent.class);

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
        a.handleEvent(new Event("topic", new PresenceUpdateNotificationEvent(System.currentTimeMillis(), null, null, null).getProperties()));
        assertEquals(1, events.size());
        assertEquals(0, events2.size());
        assertEquals(1, events3.size());
    }
}
