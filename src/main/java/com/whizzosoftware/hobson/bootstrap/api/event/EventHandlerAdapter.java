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

import com.whizzosoftware.hobson.api.event.EventCallbackInvoker;
import com.whizzosoftware.hobson.api.event.EventHandler;
import com.whizzosoftware.hobson.api.event.HobsonEvent;
import com.whizzosoftware.hobson.bootstrap.api.util.EventUtil;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Adapts the OSGi EventAdmin event callback (handleEvent) into a reflection-based invocation of a @EventHandler
 * annotated method in the listener.
 *
 * @author Dan Noguerol
 */
public class EventHandlerAdapter implements org.osgi.service.event.EventHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private EventFactory eventFactory;
    private Object listener;
    private EventCallbackInvoker invoker;

    public EventHandlerAdapter(EventFactory eventFactory, Object listener, EventCallbackInvoker invoke) {
        this.eventFactory = eventFactory;
        this.listener = listener;
        this.invoker = invoke;
    }

    @Override
    public void handleEvent(Event event) {
        logger.trace("Received event: {}", event);

        Map<String, Object> props = EventUtil.createMapFromEvent(event);

        if (listener != null) {
            HobsonEvent he = eventFactory.createEvent(props);
            if (he != null) {
                for (Method m : listener.getClass().getMethods()) {
                    if (m.isAnnotationPresent(EventHandler.class)) {
                        Class[] params = m.getParameterTypes();
                        if (params.length == 1 && params[0].isAssignableFrom(he.getClass())) {
                            invoker.invoke(m, listener, he);
                        }
                    }
                }
            } else {
                logger.error("Unable to unmarshal event: {}", props);
            }
        } else {
            logger.warn("No event listener registered; ignoring event {}", HobsonEvent.readEventId(props));
        }
    }
}
