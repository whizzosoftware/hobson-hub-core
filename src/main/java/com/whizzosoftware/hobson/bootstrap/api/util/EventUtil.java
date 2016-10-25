/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.util;

import com.whizzosoftware.hobson.api.event.EventTopics;
import com.whizzosoftware.hobson.api.event.HobsonEvent;
import org.osgi.service.event.Event;

import java.util.HashMap;
import java.util.Map;

/**
 * A convenience class for performing various event related functions.
 *
 * @author Dan Noguerol
 */
public class EventUtil {
    public static final String PROP_EVENT_ID = "eventId";

    static public Event createEventFromHobsonEvent(HobsonEvent event) {
        Map map = new HashMap();
        map.put(PROP_EVENT_ID, event.getEventId());

        // copy over all event-specific properties
        Map propMap = event.getProperties();
        if (propMap != null) {
            for (Object key : propMap.keySet()) {
                map.put(key, propMap.get(key));
            }
        }

        return new Event(EventTopics.GLOBAL, map);
    }

    static public Map<String,Object> createMapFromEvent(Event event) {
        Map<String,Object> map = new HashMap<>();
        for (String key : event.getPropertyNames()) {
            map.put(key, event.getProperty(key));
        }
        return map;
    }
}
