/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
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
    private static final Logger logger = LoggerFactory.getLogger(OSGIEventManager.class);

    volatile private BundleContext bundleContext;
    volatile private EventAdmin eventAdmin;

    private final Map<EventListener,ServiceRegistration> serviceRegMap = new HashMap<EventListener,ServiceRegistration>();
    private final EventFactory eventFactory = new EventFactory();

    public OSGIEventManager() {
        try {
            eventFactory.addEventClass(VariableUpdateNotificationEvent.ID, VariableUpdateNotificationEvent.class);
            eventFactory.addEventClass(VariableUpdateRequestEvent.ID, VariableUpdateRequestEvent.class);
            eventFactory.addEventClass(PresenceUpdateEvent.ID, PresenceUpdateEvent.class);
            eventFactory.addEventClass(DeviceAdvertisementEvent.ID, DeviceAdvertisementEvent.class);
            eventFactory.addEventClass(HubConfigurationUpdateEvent.ID, HubConfigurationUpdateEvent.class);
            eventFactory.addEventClass(PluginConfigurationUpdateEvent.ID, PluginConfigurationUpdateEvent.class);
            eventFactory.addEventClass(DeviceConfigurationUpdateEvent.ID, DeviceConfigurationUpdateEvent.class);
        } catch (NoSuchMethodException e) {
            logger.error("An error occurred during event registration", e);
        }
    }

    @Override
    public void addListener(HubContext ctx, EventListener listener, String[] topics) {
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
    public void removeListener(HubContext ctx, EventListener listener, String[] topics) {
        // TODO
    }

    @Override
    public void removeListenerFromAllTopics(HubContext ctx, EventListener listener) {
        synchronized (serviceRegMap) {
            ServiceRegistration reg = serviceRegMap.get(listener);
            if (reg != null) {
                reg.unregister();
                serviceRegMap.remove(listener);
            }
        }
    }

    @Override
    public void postEvent(HubContext ctx, HobsonEvent event) {
        logger.trace("Posting event for {}: {}", ctx, event);
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
            logger.trace("Received event: {}", event);

            Map<String, Object> props = EventUtil.createMapFromEvent(event);

            if (listener != null) {
                HobsonEvent he = eventFactory.createEvent(props);
                if (he != null) {
                    listener.onHobsonEvent(he);
                }
            } else {
                logger.warn("No event listener registered; ignoring event {}", HobsonEvent.readEventId(props));
            }
        }
    }
}
