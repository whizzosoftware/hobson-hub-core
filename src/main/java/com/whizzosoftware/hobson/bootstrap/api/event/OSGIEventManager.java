/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.event;

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.advertisement.DeviceAdvertisementEvent;
import com.whizzosoftware.hobson.api.event.device.*;
import com.whizzosoftware.hobson.api.event.hub.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.plugin.PluginConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.plugin.PluginStartedEvent;
import com.whizzosoftware.hobson.api.event.plugin.PluginStoppedEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateRequestEvent;
import com.whizzosoftware.hobson.api.event.task.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.bootstrap.api.util.EventUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * An OSGi implementation of EventManager.
 *
 * @author Dan Noguerol
 */
public class OSGIEventManager implements EventManager, EventCallbackInvoker {
    private static final Logger logger = LoggerFactory.getLogger(OSGIEventManager.class);

    volatile private EventAdmin eventAdmin;

    private final Map<Object,ServiceRegistration> serviceRegMap = new HashMap<>();
    private final EventFactory eventFactory = new EventFactory();

    public OSGIEventManager() {
        try {
            eventFactory.addEventClass(DeviceAdvertisementEvent.ID, DeviceAdvertisementEvent.class);
            eventFactory.addEventClass(DeviceAvailableEvent.ID, DeviceAvailableEvent.class);
            eventFactory.addEventClass(DeviceCheckInEvent.ID, DeviceCheckInEvent.class);
            eventFactory.addEventClass(DeviceConfigurationUpdateEvent.ID, DeviceConfigurationUpdateEvent.class);
            eventFactory.addEventClass(DeviceDeletedEvent.ID, DeviceDeletedEvent.class);
            eventFactory.addEventClass(DeviceStartedEvent.ID, DeviceStartedEvent.class);
            eventFactory.addEventClass(DeviceStoppedEvent.ID, DeviceStoppedEvent.class);
            eventFactory.addEventClass(DeviceUnavailableEvent.ID, DeviceUnavailableEvent.class);
            eventFactory.addEventClass(DeviceVariablesUpdateEvent.ID, DeviceVariablesUpdateEvent.class);
            eventFactory.addEventClass(DeviceVariablesUpdateRequestEvent.ID, DeviceVariablesUpdateRequestEvent.class);
            eventFactory.addEventClass(HubConfigurationUpdateEvent.ID, HubConfigurationUpdateEvent.class);
            eventFactory.addEventClass(PluginConfigurationUpdateEvent.ID, PluginConfigurationUpdateEvent.class);
            eventFactory.addEventClass(PluginStartedEvent.ID, PluginStartedEvent.class);
            eventFactory.addEventClass(PluginStoppedEvent.ID, PluginStoppedEvent.class);
            eventFactory.addEventClass(PresenceUpdateNotificationEvent.ID, PresenceUpdateNotificationEvent.class);
            eventFactory.addEventClass(PresenceUpdateRequestEvent.ID, PresenceUpdateRequestEvent.class);
            eventFactory.addEventClass(TaskDeletedEvent.ID, TaskDeletedEvent.class);
            eventFactory.addEventClass(TaskExecutionEvent.ID, TaskExecutionEvent.class);
            eventFactory.addEventClass(TaskRegistrationEvent.ID, TaskRegistrationEvent.class);
            eventFactory.addEventClass(TaskUpdatedEvent.ID, TaskUpdatedEvent.class);
        } catch (NoSuchMethodException e) {
            logger.error("An error occurred during event registration", e);
        }
    }

    @Override
    public void addListener(HubContext ctx, Object listener) {
        addListener(ctx, listener, this);
    }

    @Override
    public void addListener(HubContext ctx, Object listener, EventCallbackInvoker invoker) {
        Hashtable ht = new Hashtable();
        ht.put(EventConstants.EVENT_TOPIC, EventTopics.GLOBAL);

        synchronized (serviceRegMap) {
            if (serviceRegMap.containsKey(listener)) {
                serviceRegMap.get(listener).unregister();
            }
            Bundle bundle = FrameworkUtil.getBundle(getClass());
            if (bundle != null) {
                BundleContext context = bundle.getBundleContext();
                if (context != null) {
                    ServiceRegistration sr = context.registerService(EventHandler.class.getName(), new EventHandlerAdapter(eventFactory, listener, invoker), ht);
                    if (sr != null) {
                        serviceRegMap.put(listener, sr);
                    } else {
                        logger.error("Received null service registration registering listener: " + listener);
                    }
                }
            }
        }
    }

    @Override
    public void removeListener(HubContext ctx, Object listener) {
        // TODO
    }

    @Override
    public void postEvent(HubContext ctx, HobsonEvent event) {
        logger.trace("Posting event for {}: {}", ctx, event);
        eventAdmin.postEvent(EventUtil.createEventFromHobsonEvent(event));
    }

    @Override
    public void invoke(Method m, Object o, HobsonEvent e) {
        try {
            m.invoke(o, e);
        } catch (Throwable t) {
            logger.error("Error invoking event callback", t);
        }
    }
}
