/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.*;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * A factory for converting property maps into HobsonEvent instances.
 *
 * @author Dan Noguerol
 */
public class EventFactory {
    private final Map<String,Constructor> classMap = new HashMap<>();

    protected void addEventClass(String id, Class clazz) throws NoSuchMethodException {
        Constructor c = clazz.getConstructor(Map.class);
        classMap.put(id, c);
    }

    public HobsonEvent createEvent(Map<String,Object> props) {
        HobsonEvent event = null;
        String eventId = HobsonEvent.readEventId(props);
        Constructor c = classMap.get(eventId);

        if (c != null) {
            try {
                event = (HobsonEvent)c.newInstance(props);
            } catch (Exception e) {
                throw new HobsonRuntimeException("Error creating event: " + props, e);
            }
        }

        return event;
    }
}
