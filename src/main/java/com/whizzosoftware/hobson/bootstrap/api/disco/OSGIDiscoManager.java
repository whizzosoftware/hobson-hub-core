/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.disco;

import com.whizzosoftware.hobson.api.disco.*;
import com.whizzosoftware.hobson.api.event.DeviceAdvertisementListenerPublishedEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * An OSGi implementation of DiscoManager.
 *
 * Note: This is currently an in-memory implementation.
 *
 * @author Dan Noguerol
 */
public class OSGIDiscoManager implements DiscoManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile ExecutorService executorService;

    private final Map<String,List<DeviceAdvertisementListener>> listenerMap = new HashMap<>();

    public void start() {
        logger.debug("OSGIDiscoManager starting");
    }

    public void stop() {
        logger.debug("OSGIDiscoManager stopping");

        listenerMap.clear();
    }

    @Override
    public void publishDeviceAdvertisementListener(String userId, String hubId, String protocolId, DeviceAdvertisementListener listener) {
        synchronized (listenerMap) {
            List<DeviceAdvertisementListener> listenerList = listenerMap.get(protocolId);
            if (listenerList == null) {
                listenerList = new ArrayList<DeviceAdvertisementListener>();
                listenerMap.put(protocolId, listenerList);
            }
            if (!listenerList.contains(listener)) {
                listenerList.add(listener);
            }
        }
        eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new DeviceAdvertisementListenerPublishedEvent(listener));
    }

    @Override
    public void unpublishDeviceAdvertisementListener(String userId, String hubId, DeviceAdvertisementListener listener) {
        synchronized (listenerMap) {
            for (String protocolId : listenerMap.keySet()) {
                List<DeviceAdvertisementListener> listenerList = listenerMap.get(protocolId);
                if (listenerList != null && listenerList.contains(listener)) {
                    listenerList.remove(listener);
                }
            }
        }
    }

    @Override
    public void fireDeviceAdvertisement(String userId, String hubId, final DeviceAdvertisement advertisement) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                synchronized (listenerMap) {
                    List<DeviceAdvertisementListener> listeners = listenerMap.get(advertisement.getProtocolId());
                    if (listeners != null) {
                        for (DeviceAdvertisementListener listener : listeners) {
                            listener.onDeviceAdvertisement(advertisement);
                        }
                    }
                }
            }
        });
    }
}
