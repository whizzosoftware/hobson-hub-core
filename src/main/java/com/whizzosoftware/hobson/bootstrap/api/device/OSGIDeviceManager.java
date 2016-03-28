/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.device.store.DevicePassportStore;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.EventLoopExecutor;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.device.store.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * An OSGi implementation of DeviceManager.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceManager implements DeviceManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    volatile private BundleContext bundleContext;
    volatile private EventManager eventManager;
    volatile private ConfigurationAdmin configAdmin;
    volatile private VariableManager variableManager;
    volatile private PluginManager pluginManager;

    private Map<DeviceContext,Boolean> deviceAvailabilityMap = Collections.synchronizedMap(new HashMap<DeviceContext,Boolean>());
    private Map<DeviceContext,Long> deviceCheckIns = Collections.synchronizedMap(new HashMap<DeviceContext,Long>());
    private DeviceStore deviceStore;
    private DevicePassportStore passportStore;
    private DeviceAvailabilityMonitor deviceAvailabilityMonitor;
    private ScheduledExecutorService deviceAvailabilityExecutor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Device Availability Monitor");
        }
    });
    private ScheduledFuture deviceAvailabilityFuture;

    public void start() {
        // if a device store hasn't already been injected, create a default one
        if (deviceStore == null) {
            deviceStore = new OSGIDeviceStore(bundleContext, new MapDBDeviceConfigurationStore(pluginManager.getDataFile(null, "deviceConfig")), eventManager, pluginManager);
            deviceStore.start();
        }

        // if a bootstrap store hasn't already been injected, create a default one
        if (passportStore == null) {
            this.passportStore = new MapDBDevicePassportStore(
                pluginManager.getDataFile(
                    PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                    "bootstraps"
                )
            );
        }

        // start device availability monitor
        deviceAvailabilityMonitor = new DeviceAvailabilityMonitor(HubContext.createLocal(), this, eventManager);
        deviceAvailabilityFuture = deviceAvailabilityExecutor.scheduleAtFixedRate(
            deviceAvailabilityMonitor,
            Math.min(30, HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL),
            Math.min(30, HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL),
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        // stop the device store
        deviceStore.stop();

        // stop the device availability monitor
        if (deviceAvailabilityFuture != null) {
            deviceAvailabilityFuture.cancel(true);
        }
    }

    @Override
    public DevicePassport createDevicePassport(HubContext hubContext, String deviceId) {
        // make sure a bootstrap hasn't already been created for this device ID
        if (passportStore.hasPassportForDeviceId(hubContext, deviceId)) {
            return null;
        }

        // create the device bootstrap
        DevicePassport db = new DevicePassport(hubContext, UUID.randomUUID().toString(), deviceId, System.currentTimeMillis());
        db.setSecret(UUID.randomUUID().toString());
        passportStore.savePassport(hubContext, db);
        return db;
    }

    @Override
    public Collection<DevicePassport> getDevicePassports(HubContext hubContext) {
        List<DevicePassport> results = new ArrayList<>();
        for (DevicePassport db : passportStore.getAllPassports(hubContext)) {
            results.add(db);
        }
        return results;
    }

    @Override
    public DevicePassport getDevicePassport(HubContext hubContext, String id) {
        return passportStore.getPassport(hubContext, id);
    }

    @Override
    public DevicePassport activateDevicePassport(HubContext hubContext, String deviceId) {
        DevicePassport bootstrap = passportStore.getPassportForDeviceId(hubContext, deviceId);
        if (bootstrap != null) {
            if (bootstrap.getActivationTime() == null) {
                bootstrap.setActivationTime(System.currentTimeMillis());
                passportStore.savePassport(hubContext, bootstrap);
                return bootstrap;
            } else {
                throw new DevicePassportAlreadyActivatedException(bootstrap.getId());
            }
        } else {
            throw new DevicePassportNotFoundException();
        }
    }

    @Override
    public void deleteDevicePassport(HubContext hubContext, String id) {
        passportStore.deletePassport(hubContext, id);
    }

    @Override
    public boolean verifyDevicePassport(HubContext hubContext, String id, String secret) {
        DevicePassport db = passportStore.getPassport(hubContext, id);
        return (db != null && secret.equals(db.getSecret()));
    }

    @Override
    public void resetDevicePassport(HubContext hubContext, String id) {
        DevicePassport db = passportStore.getPassport(hubContext, id);
        if (db != null) {
            db.setActivationTime(null);
            passportStore.savePassport(hubContext, db);
        }
    }

    @Override
    public void setDeviceAvailability(DeviceContext ctx, boolean available, Long checkInTime) {
        deviceAvailabilityMap.put(ctx, available);
        if (checkInTime != null) {
            deviceCheckIns.put(ctx, checkInTime);
            if (eventManager != null) {
                eventManager.postEvent(ctx.getHubContext(), new DeviceCheckInEvent(ctx, System.currentTimeMillis()));
            }
        }
    }

    @Override
    public Collection<HobsonDevice> getAllDevices(HubContext ctx) {
        return deviceStore.getAllDevices(ctx);
    }

    @Override
    public Collection<HobsonDevice> getAllDevices(PluginContext ctx) {
        return deviceStore.getAllDevices(ctx);
    }

    @Override
    public HobsonDevice getDevice(DeviceContext ctx) {
        return deviceStore.getDevice(ctx);
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx) {
        return deviceStore.getDeviceConfiguration(ctx);
    }

    @Override
    public PropertyContainerClass getDeviceConfigurationClass(DeviceContext ctx) {
        return getDevice(ctx).getConfigurationClass();
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        return deviceStore.getDeviceConfigurationProperty(ctx, name);
    }

    @Override
    public boolean isDeviceAvailable(DeviceContext ctx) {
        return isDeviceAvailable(ctx, System.currentTimeMillis());
    }

    public boolean isDeviceAvailable(DeviceContext ctx, long now) {
        Long checkin = deviceCheckIns.get(ctx);
        boolean stale = (checkin == null || now - checkin >= HobsonDevice.AVAILABILITY_TIMEOUT_INTERVAL);
        Boolean avail = deviceAvailabilityMap.get(ctx);
        return (!stale && avail != null && avail);
    }

    @Override
    public Long getDeviceLastCheckIn(DeviceContext ctx) {
        return deviceCheckIns.get(ctx);
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return deviceStore.hasDevice(ctx);
    }

    @Override
    public void publishDevice(HobsonDevice device) {
        publishDevice(device, false);
    }

    @Override
    synchronized public void publishDevice(HobsonDevice device, boolean republish) {
        publishDevice(device, republish, System.currentTimeMillis());
    }

    public void publishDevice(HobsonDevice device, boolean republish, long now) {
        String deviceId = device.getContext().getDeviceId();

        // check that the device ID is legal
        if (deviceId == null || deviceId.contains(",") || deviceId.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish device \"" + deviceId + "\": the ID is either null or contains an invalid character");
        }

        // publish the device
        if (deviceStore != null) {
            deviceStore.publishDevice(device, republish);
        }

        // we assume that since the device is published, it's availability is true
        setDeviceAvailability(device.getContext(), true, now);
    }

    @Override
    synchronized public void unpublishDevice(final DeviceContext ctx, final EventLoopExecutor executor) {
        unpublishDevice(getDevice(ctx), executor);

    }

    synchronized public void unpublishDevice(final HobsonDevice device, final EventLoopExecutor executor) {
        if (device != null) {
            // execute the device's shutdown method using its plugin event loop
            executor.executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    try {
                        device.getRuntime().onShutdown();
                        logger.debug("Device {} has shut down", device.getContext());
                    } catch (Throwable t) {
                        logger.error("Error shutting down device: " + device.getContext(), t);
                    } finally {
                        deviceStore.unpublishDevice(device.getContext());
                        eventManager.postEvent(device.getContext().getHubContext(), new DeviceStoppedEvent(System.currentTimeMillis(), device.getContext()));
                    }
                }
            });
        }
    }

    @Override
    synchronized public void unpublishAllDevices(final PluginContext ctx, final EventLoopExecutor executor) {
        for (HobsonDevice device : deviceStore.getAllDevices(ctx)) {
            unpublishDevice(device, executor);
        }
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext ctx, String name, Object value, boolean overwrite) {
        setDeviceConfigurationProperties(ctx, Collections.singletonMap(name, value), overwrite);
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, Map<String,Object> values, boolean overwrite) {
        deviceStore.setDeviceConfigurationProperties(ctx, values, overwrite);
    }
}
