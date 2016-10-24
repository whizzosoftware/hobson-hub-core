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
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
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

    volatile private EventAdmin eventAdmin;

    private final Map<EventListener,ServiceRegistration> serviceRegMap = new HashMap<EventListener,ServiceRegistration>();
    private final EventFactory eventFactory = new EventFactory();

    public OSGIEventManager() {
        try {
            eventFactory.addEventClass(DeviceAdvertisementEvent.ID, DeviceAdvertisementEvent.class);
            eventFactory.addEventClass(DeviceCheckInEvent.ID, DeviceCheckInEvent.class);
            eventFactory.addEventClass(DeviceConfigurationUpdateEvent.ID, DeviceConfigurationUpdateEvent.class);
            eventFactory.addEventClass(DeviceStartedEvent.ID, DeviceStartedEvent.class);
            eventFactory.addEventClass(DeviceStoppedEvent.ID, DeviceStoppedEvent.class);
            eventFactory.addEventClass(DeviceUnavailableEvent.ID, DeviceUnavailableEvent.class);
            eventFactory.addEventClass(HubConfigurationUpdateEvent.ID, HubConfigurationUpdateEvent.class);
            eventFactory.addEventClass(PluginConfigurationUpdateEvent.ID, PluginConfigurationUpdateEvent.class);
            eventFactory.addEventClass(PluginStartedEvent.ID, PluginStartedEvent.class);
            eventFactory.addEventClass(PluginStoppedEvent.ID, PluginStoppedEvent.class);
            eventFactory.addEventClass(PresenceUpdateNotificationEvent.ID, PresenceUpdateNotificationEvent.class);
            eventFactory.addEventClass(PresenceUpdateRequestEvent.ID, PresenceUpdateRequestEvent.class);
            eventFactory.addEventClass(TaskCreatedEvent.ID, TaskCreatedEvent.class);
            eventFactory.addEventClass(TaskDeletedEvent.ID, TaskDeletedEvent.class);
            eventFactory.addEventClass(TaskExecutionEvent.ID, TaskExecutionEvent.class);
            eventFactory.addEventClass(TaskRegistrationEvent.ID, TaskRegistrationEvent.class);
            eventFactory.addEventClass(TaskUpdatedEvent.ID, TaskUpdatedEvent.class);
            eventFactory.addEventClass(DeviceVariableUpdateEvent.ID, DeviceVariableUpdateEvent.class);
        } catch (NoSuchMethodException e) {
            logger.error("An error occurred during event registration", e);
        }
    }

    @Override
    public void addListener(HubContext ctx, EventListener listener, String[] topics) {
        if (topics != null) {
            Hashtable ht = new Hashtable();
            ht.put(EventConstants.EVENT_TOPIC, topics);

            synchronized (serviceRegMap) {
                if (serviceRegMap.containsKey(listener)) {
                    serviceRegMap.get(listener).unregister();
                }
                Bundle bundle = FrameworkUtil.getBundle(getClass());
                if (bundle != null) {
                    BundleContext context = bundle.getBundleContext();
                    if (context != null) {
                        ServiceRegistration sr = context.registerService(EventHandler.class.getName(), new EventHandlerAdapter(listener), ht);
                        if (sr != null) {
                            serviceRegMap.put(
                                listener,
                                sr
                            );
                        } else {
                            logger.error("Received null service registration registering listener: " + listener);
                        }
                    }
                }
            }
        } else {
            logger.error("Ignoring null topic subscription");
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
                } else {
                    logger.error("Unable to unmarshal event: {}", props);
                }
            } else {
                logger.warn("No event listener registered; ignoring event {}", HobsonEvent.readEventId(props));
            }
        }
    }
}
