/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.bootstrap.api.util.EventUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * An OSGi implementation of EventManager.
 *
 * @author Dan Noguerol
 */
public class OSGIEventManager implements EventManager {
    volatile private BundleContext bundleContext;
    volatile private EventAdmin eventAdmin;

    private final Map<EventListener,ServiceRegistration> serviceRegMap = new HashMap<EventListener,ServiceRegistration>();

    @Override
    public void addListener(String userId, String hubId, EventListener listener, String[] topics) {
        Hashtable ht = new Hashtable();
        ht.put(EventConstants.EVENT_TOPIC, topics);
        synchronized (serviceRegMap) {
            if (serviceRegMap.containsKey(listener)) {
                serviceRegMap.get(listener).unregister();
            }
            serviceRegMap.put(
                listener,
                bundleContext.registerService(EventHandler.class.getName(), new EventHandlerAdapter(listener), ht)
            );
        }
    }

    @Override
    public void removeListener(String userId, String hubId, EventListener listener, String[] topics) {
        // TODO
    }

    @Override
    public void removeListenerFromAllTopics(String userId, String hubId, EventListener listener) {
        synchronized (serviceRegMap) {
            ServiceRegistration reg = serviceRegMap.get(listener);
            if (reg != null) {
                reg.unregister();
                serviceRegMap.remove(listener);
            }
        }
    }

    @Override
    public void postEvent(String userId, String hubId, HobsonEvent event) {
        eventAdmin.postEvent(EventUtil.createEventFromHobsonEvent(event));
    }

    private class EventHandlerAdapter implements EventHandler {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        private EventListener listener;

        public EventHandlerAdapter(EventListener listener) {
            this.listener = listener;
        }

        @Override
        public void handleEvent(Event event) {
            // TODO: find a more elegant way to perform OSGi -> Hobson event conversion
            logger.trace("Received event: {}", event);

            Map<String,Object> props = EventUtil.createMapFromEvent(event);

            if (VariableUpdateNotificationEvent.ID.equals(HobsonEvent.readEventId(props))) {
                listener.onHobsonEvent(new VariableUpdateNotificationEvent(props));
            } else if (VariableUpdateRequestEvent.ID.equals(HobsonEvent.readEventId(props))) {
                listener.onHobsonEvent(new VariableUpdateRequestEvent(props));
            } else if (PresenceUpdateEvent.ID.equals(HobsonEvent.readEventId(props))) {
                listener.onHobsonEvent(new PresenceUpdateEvent(props));
            } else if (DeviceAdvertisementEvent.ID.equals(HobsonEvent.readEventId(props))) {
                listener.onHobsonEvent(new DeviceAdvertisementEvent(props));
            } else if (HubConfigurationUpdateEvent.ID.equals(HobsonEvent.readEventId(props))) {
                listener.onHobsonEvent(new HubConfigurationUpdateEvent(props));
            }
        }
    }
}
