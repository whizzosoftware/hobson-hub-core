/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.device.proxy.HobsonDeviceProxy;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescriptor;
import com.whizzosoftware.hobson.api.variable.DeviceVariableState;
import com.whizzosoftware.hobson.bootstrap.api.device.store.*;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor.AVAILABILITY_TIMEOUT_INTERVAL;

/**
 * An OSGi implementation of DeviceManager.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceManager implements DeviceManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    volatile private ConfigurationManager configManager;
    volatile private EventManager eventManager;
    volatile private PluginManager pluginManager;

    private DeviceStore deviceStore;
    private final Set<String> variableNameSet = new HashSet<>();
    private ScheduledExecutorService deviceAvailabilityExecutor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Device Availability Monitor");
        }
    });
    private ScheduledFuture deviceAvailabilityFuture;

    public void setConfigManager(ConfigurationManager configManager) {
        this.configManager = configManager;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public void setDeviceStore(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    public void start() {
        logger.debug("Device manager is starting");

        // if a device store hasn't already been injected, create a default one
        if (deviceStore == null) {
            deviceStore = new MapDBDeviceStore(
                pluginManager.getDataFile(
                    PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                    "devices"
                )
            );
            deviceStore.start();
        }

        // start device availability monitor
        DeviceAvailabilityMonitor deviceAvailabilityMonitor = new DeviceAvailabilityMonitor(HubContext.createLocal(), this, eventManager);
        deviceAvailabilityFuture = deviceAvailabilityExecutor.scheduleAtFixedRate(
                deviceAvailabilityMonitor,
            Math.min(30, AVAILABILITY_TIMEOUT_INTERVAL),
            Math.min(30, AVAILABILITY_TIMEOUT_INTERVAL),
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        logger.debug("Device manager is stopping");

        // stop the device store
        deviceStore.stop();

        // stop the device availability monitor
        if (deviceAvailabilityFuture != null) {
            deviceAvailabilityFuture.cancel(true);
        }
    }

    @Override
    public HobsonDeviceDescriptor getDevice(DeviceContext dctx) {
        if (deviceStore != null) {
            HobsonDeviceDescriptor hdd = deviceStore.getDevice(dctx);
            if (hdd != null) {
                return hdd;
            } else {
                throw new HobsonNotFoundException("Device does not exist: " + dctx);
            }
        } else {
            throw new HobsonRuntimeException("No device store is available");
        }
    }

    @Override
    public Future setDeviceVariable(final DeviceVariableContext ctx, final Object value) {
        return pluginManager.setLocalPluginDeviceVariable(ctx, value);
    }

    @Override
    public Future setDeviceVariables(final Map<DeviceVariableContext,Object> map) {
        for (final DeviceVariableContext dvctx : map.keySet()) {
            setDeviceVariable(dvctx, map.get(dvctx)); // TODO: single setDeviceVariables call?
        }
        return null; // TODO
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx) {
        HobsonDeviceDescriptor device = deviceStore.getDevice(ctx);
        if (device != null) {
            PropertyContainerClass configClass = device.getConfigurationClass();
            if (configClass != null) {
                return configManager.getDeviceConfiguration(ctx, configClass);
            }
        }
        return null;
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        return configManager.getDeviceConfigurationProperty(ctx, name);
    }

    @Override
    public Long getDeviceLastCheckin(DeviceContext dctx) {
        return pluginManager.getLocalPluginDeviceLastCheckin(dctx.getPluginContext(), dctx.getDeviceId());
    }

    @Override
    public DeviceVariableState getDeviceVariable(DeviceVariableContext ctx) {
        return pluginManager.getLocalPluginDeviceVariable(ctx);
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getDevices(HubContext hctx) {
        return deviceStore.getAllDevices(hctx);
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getDevices(PluginContext pctx) {
        return deviceStore.getAllDevices(pctx);
    }

    @Override
    public Collection<String> getDeviceVariableNames(HubContext hctx) {
        return variableNameSet;
    }

    @Override
    public boolean isDeviceAvailable(DeviceContext ctx) {
        return isDeviceAvailable(ctx, System.currentTimeMillis());
    }

    boolean isDeviceAvailable(DeviceContext ctx, long now) {
        Long lastCheckin = getDeviceLastCheckin(ctx);
        return (lastCheckin != null && now - lastCheckin < AVAILABILITY_TIMEOUT_INTERVAL);
    }

    protected String getDeviceName(DeviceContext ctx) {
        if (configManager != null) {
            return configManager.getDeviceName(ctx);
        } else {
            return null;
        }
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return deviceStore.hasDevice(ctx);
    }

    @Override
    public Future publishDevice(HobsonDeviceProxy proxy, Map<String, Object> config, Runnable runnable) {
        return publishDevice(
            proxy,
            new PropertyContainer(PropertyContainerClassContext.create(DeviceContext.create(proxy.getContext().getPluginContext(), proxy.getContext().getDeviceId()), "configuration"), config),
            runnable
        );
    }

    synchronized Future publishDevice(final HobsonDeviceProxy device, final PropertyContainer config, final Runnable runnable) {
        final String deviceId = device.getContext().getDeviceId();

        // check that the device ID is legal
        if (deviceId == null || deviceId.contains(",") || deviceId.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish device \"" + deviceId + "\": the ID is either null or contains an invalid character");
        }

        // if an explicit name has not been set for the device, use the default one
        String name = getDeviceName(device.getContext());
        if (name == null) {
            name = device.getDefaultName();
        }

        // request that the plugin manager start the device
        return pluginManager.startPluginDevice(device, name, config, new Runnable() {
            @Override
            public void run() {
                // collect its list of variables (for use by the getDeviceVariableNames() method)
                Collection<DeviceVariableDescriptor> vars = device.getDescriptor().getVariables();
                if (vars != null) {
                    for (DeviceVariableDescriptor dv : vars) {
                        if (!variableNameSet.contains(dv.getContext().getName())) {
                            variableNameSet.add(dv.getContext().getName());
                        }
                    }
                }

                // persist the device description
                if (deviceStore != null) {
                    deviceStore.saveDevice(device.getDescriptor());
                }

                // post the device started event
                if (eventManager != null) {
                    eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(System.currentTimeMillis(), device.getContext()));
                }

                if (runnable != null) {
                    runnable.run();
                }
            }
        });
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext dctx, PropertyContainerClass configClass, String name, Object value) {
        configManager.setDeviceConfigurationProperty(dctx, configClass, name, value);

        // send update event
        eventManager.postEvent(
                dctx.getHubContext(),
                new DeviceConfigurationUpdateEvent(
                        System.currentTimeMillis(),
                        dctx.getPluginId(),
                        dctx.getDeviceId(),
                        configManager.getDeviceConfiguration(dctx, configClass)
                )
        );
    }

    @Override
    public void setDeviceConfiguration(DeviceContext dctx, PropertyContainerClass configClass, Map<String, Object> values) {
        setDeviceConfigurationProperties(dctx, configClass, values, true);
    }

    @Override
    public void setDeviceName(DeviceContext dctx, String name) {
        configManager.setDeviceName(dctx, name);
    }

    private void setDeviceConfigurationProperties(DeviceContext dctx, PropertyContainerClass configClass, Map<String,Object> values, boolean sendEvent) {
        configManager.setDeviceConfigurationProperties(dctx, configClass, values);

        // send update event
        if (sendEvent) {
            eventManager.postEvent(
                dctx.getHubContext(),
                new DeviceConfigurationUpdateEvent(
                    System.currentTimeMillis(),
                    dctx.getPluginId(),
                    dctx.getDeviceId(),
                    configManager.getDeviceConfiguration(dctx, configClass)
                )
            );
        }
    }

    @Override
    public void setDeviceTags(DeviceContext deviceContext, Set<String> set) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDevice(HobsonDeviceDescriptor device) {
        logger.debug("Updating device descriptor for: {}", device.getContext());
        // persist the device description
        if (deviceStore != null) {
            deviceStore.saveDevice(device);
        }
    }
}
