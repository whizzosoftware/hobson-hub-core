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
import com.whizzosoftware.hobson.api.event.device.DeviceAdvertisementEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    private final Map<String,ServiceRegistration> localDiscoveryList = new HashMap<>();
    private final Map<String,ServiceRegistration> localPublishList = new HashMap<>();

    public void start() {
        logger.debug("OSGIDiscoManager starting");
    }

    public void stop() {
        logger.debug("OSGIDiscoManager stopping");

        // unregister all previous registered services
        for (ServiceRegistration sr : localDiscoveryList.values()) {
            sr.unregister();
        }
        localDiscoveryList.clear();

        for (ServiceRegistration sr : localPublishList.values()) {
            sr.unregister();
        }
        localPublishList.clear();
    }

    @Override
    synchronized public Collection<DeviceAdvertisement> getExternalDeviceAdvertisements(PluginContext ctx, String protocolId) {
        try {
            List<DeviceAdvertisement> results = null;
            BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + protocolId + ")(internal=false))");
            if (references != null && references.length > 0) {
                results = new ArrayList<>();
                for (ServiceReference ref : references) {
                    results.add((DeviceAdvertisement)context.getService(ref));
                }
            } else {
                logger.debug("No device advertisements found to re-send");
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error delivering device advertisement snapshot", e);
        }
    }

    @Override
    synchronized public Collection<DeviceAdvertisement> getInternalDeviceAdvertisements(HubContext ctx, String protocolId) {
        try {
            BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + protocolId + ")(internal=true))");
            if (references != null && references.length > 0) {
                List<DeviceAdvertisement> advs = new ArrayList<>();
                for (ServiceReference ref : references) {
                    DeviceAdvertisement adv = (DeviceAdvertisement)context.getService(ref);
                    advs.add(adv);
                }
                return advs;
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving internal device advertisements", e);
        }
        return null;
    }

    @Override
    public DeviceAdvertisement getInternalDeviceAdvertisement(HubContext ctx, String protocolId, String advId) {
        try {
            BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + protocolId + ")(id=" + advId + ")(internal=true))");
            if (references != null && references.length == 1) {
                return (DeviceAdvertisement)context.getService(references[0]);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving internal device advertisements", e);
        }
        return null;
    }

    @Override
    synchronized public void publishDeviceAdvertisement(HubContext ctx, final DeviceAdvertisement advertisement, boolean internal) {
        logger.trace("Publishing device advertisement: {}", advertisement);

        String fqId = advertisement.getProtocolId() + ":" + advertisement.getId();

        if ((internal && !localPublishList.containsKey(fqId)) || (!internal && !localDiscoveryList.containsKey(fqId))) {
            try {
                // check if we've seen this advertisement before
                BundleContext context = BundleUtil.getBundleContext(getClass(), null);
                ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + DeviceAdvertisement.class.getName() + ")(protocolId=" + advertisement.getProtocolId() + ")(id=" + advertisement.getId() + ")(internal=" + internal + "))");
                if (references == null || references.length == 0) {
                    // publish the advertisement as a service if we haven't already done so
                    Hashtable props = new Hashtable();
                    props.put("protocolId", advertisement.getProtocolId());
                    props.put("id", advertisement.getId());
                    props.put("internal", internal);
                    ServiceRegistration sr = context.registerService(DeviceAdvertisement.class.getName(), advertisement, props);

                    logger.debug("Registered device advertisement: {}", fqId);
                    localDiscoveryList.put(fqId, sr);

                    // send the advertisement to interested listeners
                    eventManager.postEvent(ctx, new DeviceAdvertisementEvent(System.currentTimeMillis(), advertisement));
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
