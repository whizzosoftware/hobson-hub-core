/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.disco;

import com.whizzosoftware.hobson.api.disco.*;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An OSGi implementation of DiscoManager.
 *
 * Note: This is currently a very naive, brute-force implementation of the mechanism and doesn't have any
 * optimizations yet.
 *
 * @author Dan Noguerol
 */
public class OSGIDiscoManager implements DiscoManager, DeviceBridgeDetectionContext {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;

    private final Map<String,DeviceBridge> bridgeMap = new HashMap<String,DeviceBridge>();
    private final Map<String,ServiceRegistration> detectorRegistrationMap = new HashMap<String,ServiceRegistration>();

    public void start() {
        logger.debug("OSGIDiscoManager starting");
    }

    public void stop() {
        logger.debug("OSGIDiscoManager stopping");

        synchronized (detectorRegistrationMap) {
            for (ServiceRegistration reg : detectorRegistrationMap.values()) {
                reg.unregister();
            }
            detectorRegistrationMap.clear();
        }
    }

    @Override
    public Collection<DeviceBridge> getDeviceBridges(String userId, String hubId) {
        ArrayList<DeviceBridge> results = new ArrayList<DeviceBridge>();
        synchronized (bridgeMap) {
            for (DeviceBridge disco : bridgeMap.values()) {
                results.add(disco);
            }
        }
        return results;
    }

    @Override
    public void publishDeviceBridgeDetector(String userId, String hubId, DeviceBridgeDetector detector) {
        logger.trace("Adding device bridge detector: {}", detector.getId());
        synchronized (detectorRegistrationMap) {
            Dictionary dic = new Hashtable();
            dic.put("id", detector.getId());
            dic.put("pluginId", detector.getPluginId());
            detectorRegistrationMap.put(
                    detector.getId(),
                    bundleContext.registerService(DeviceBridgeDetector.class.getName(), detector, dic)
            );
            refreshScanners();
        }
    }

    @Override
    public void unpublishDeviceBridgeDetector(String userId, String hubId, String detectorId) {
        logger.trace("Removing device bridge detector: {}", detectorId);
        synchronized (detectorRegistrationMap) {
            ServiceRegistration reg = detectorRegistrationMap.get(detectorId);
            if (reg != null) {
                reg.unregister();
                detectorRegistrationMap.remove(detectorId);
            }
        }
    }

    @Override
    public void processDeviceBridgeMetaData(String userId, String hubId, DeviceBridgeMetaData meta) {
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(DeviceBridgeDetector.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    DeviceBridgeDetector di = (DeviceBridgeDetector)bundleContext.getService(ref);
                    if (di.identify(this, meta)) {
                        break;
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            logger.error("Error attempting to perform discovery", e);
        }
    }

    @Override
    public void addDeviceBridge(DeviceBridge bridge) {
        synchronized (bridgeMap) {
            logger.debug("Added device bridge: {} ({})", bridge.getValue(), bridge.getName());
            bridgeMap.put(bridge.getValue(), bridge);
        }
    }

    @Override
    public void removeDeviceBridge(String bridgeId) {
        synchronized (bridgeMap) {
            logger.debug("Removed device bridge: {}", bridgeId);
            bridgeMap.remove(bridgeId);
        }
    }

    private void refreshScanners() {
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(DeviceBridgeScanner.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    DeviceBridgeScanner scanner = (DeviceBridgeScanner)bundleContext.getService(ref);
                    scanner.refresh();
                }
            }
        } catch (InvalidSyntaxException e) {
            logger.error("An error occurred refreshing DeviceBridgeScanners", e);
        }
    }
}
