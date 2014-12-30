/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.disco;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.disco.*;
import com.whizzosoftware.hobson.api.event.DeviceAdvertisementEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
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
    private volatile PluginManager pluginManager;
    private volatile EventManager eventManager;
    private volatile ExecutorService executorService;

    private final Map<String,ServiceRegistration> localDiscoveryList = new HashMap<>();

    public void start() {
        logger.debug("OSGIDiscoManager starting");
    }

    public void stop() {
        logger.debug("OSGIDiscoManager stopping");

        // unregister all previous registered services
        for (ServiceRegistration sr : localDiscoveryList.values()) {
            sr.unregister();
        }
    }

    @Override
    synchronized public void requestDeviceAdvertisementSnapshot(String userId, String hubId, String pluginId, String protocolId) {
        try {
            BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
            HobsonPlugin plugin = pluginManager.getPlugin(userId, hubId, pluginId);
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + protocolId + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    DeviceAdvertisement adv = (DeviceAdvertisement) context.getService(ref);
                    logger.debug("Resending device advertisement {} to plugin {}", adv.getId(), plugin);
                    plugin.onHobsonEvent(new DeviceAdvertisementEvent(adv));
                }
            } else {
                logger.debug("No device advertisements found to re-send");
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error delivering device advertisement snapshot", e);
        }
    }

    @Override
    synchronized public void fireDeviceAdvertisement(String userId, String hubId, final DeviceAdvertisement advertisement) {
        String fqId = advertisement.getProtocolId() + ":" + advertisement.getId();
        if (!localDiscoveryList.containsKey(fqId)) {
            try {
                // check if we've seen this advertisement before
                BundleContext context = BundleUtil.getBundleContext(getClass(), null);
                ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + advertisement.getProtocolId() + ")(id=" + advertisement.getId() + "))");
                if (references == null || references.length == 0) {
                    // publish the advertisement as a service if we haven't already done so
                    Hashtable props = new Hashtable();
                    props.put("protocolId", advertisement.getProtocolId());
                    props.put("id", advertisement.getId());
                    ServiceRegistration sr = context.registerService(DeviceAdvertisement.class.getName(), advertisement, props);

                    logger.debug("Registered device advertisement: {}", fqId);
                    localDiscoveryList.put(fqId, sr);

                    // send the advertisement to interested listeners
                    eventManager.postEvent(userId, hubId, new DeviceAdvertisementEvent(advertisement));
                } else {
                    logger.trace("Ignoring previously registered service: {}", fqId);
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Error querying for advertisement services", e);
            }
        } else {
            logger.trace("Ignoring duplicate advertisement: {}", fqId);
        }
    }
}
