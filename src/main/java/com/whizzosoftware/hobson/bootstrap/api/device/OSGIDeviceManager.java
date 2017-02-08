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
import com.whizzosoftware.hobson.api.event.device.DeviceConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.device.DeviceDeletedEvent;
import com.whizzosoftware.hobson.api.event.device.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.executor.ExecutorManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescriptor;
import com.whizzosoftware.hobson.api.variable.DeviceVariableState;
import com.whizzosoftware.hobson.bootstrap.api.device.store.*;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.NotSerializableException;
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

    @Inject
    volatile private ConfigurationManager configManager;
    @Inject
    volatile private EventManager eventManager;
    @Inject
    volatile private ExecutorManager executorManager;
    @Inject
    volatile private PluginManager pluginManager;

    private DeviceStore deviceStore;
    private final Set<String> variableNameSet = new HashSet<>();
    private Future availabilityFuture;
    private Future housekeepingFuture;

    public void start() {
        logger.debug("Device manager is starting");

        // if a device store hasn't already been injected, create a default one
        if (deviceStore == null) {
            deviceStore = new CachingLocalDeviceStore(new MapDBDeviceStore(
                pluginManager.getDataFile(
                    PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                    "devices"
                )
            ));
            deviceStore.start();
        }

        // start device availability monitor
        DeviceAvailabilityMonitor deviceAvailabilityMonitor = new DeviceAvailabilityMonitor(HubContext.createLocal(), this, eventManager);
        availabilityFuture = executorManager.schedule(
            deviceAvailabilityMonitor,
            Math.min(30, AVAILABILITY_TIMEOUT_INTERVAL),
            Math.min(30, AVAILABILITY_TIMEOUT_INTERVAL),
            TimeUnit.SECONDS
        );

        // create device store housekeeping task (run it starting at random interval between 22 and 24 hours)
        if (executorManager != null) {
            housekeepingFuture = executorManager.schedule(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Performing device store housekeeping");
                    try {
                        deviceStore.performHousekeeping();
                    } catch (Throwable t) {
                        logger.error("Error performing device store housekeeping", t);
                    }
                }
            }, 1440 - ThreadLocalRandom.current().nextInt(0, 121), 1440, TimeUnit.MINUTES);
        } else {
            logger.error("No executor manager available to perform device store housekeeping");
        }
    }

    public void stop() {
        logger.debug("Device manager is stopping");

        // stop the device store
        deviceStore.stop();

        // stop the device availability monitor and device store housekeeping tasks
        if (executorManager != null) {
            if (availabilityFuture != null) {
                executorManager.cancel(availabilityFuture);
            }
            if (housekeepingFuture != null) {
                executorManager.cancel(housekeepingFuture);
            }
        }
    }

    @Override
    public void deleteDevice(DeviceContext dctx) {
        if (deviceStore != null) {
            deviceStore.deleteDevice(dctx);
            eventManager.postEvent(HubContext.createLocal(), new DeviceDeletedEvent(System.currentTimeMillis(), dctx));
        } else {
            throw new HobsonRuntimeException("No device store is available");
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
    public Collection<HobsonDeviceDescriptor> getDevices(HubContext hctx) {
        return deviceStore.getAllDevices(hctx);
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getDevices(PluginContext pctx) {
        return deviceStore.getAllDevices(pctx);
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx) {
        HobsonDeviceDescriptor device = deviceStore.getDevice(ctx);
        if (device != null) {
            PropertyContainerClass configClass = device.getConfigurationClass();
            if (configClass != null) {
                return new PropertyContainer(configClass.getContext(), configManager.getDeviceConfiguration(ctx));
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
    public Collection<String> getDeviceVariableNames(HubContext hctx) {
        return variableNameSet;
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return deviceStore.hasDevice(ctx);
    }

    @Override
    public boolean hasDeviceVariable(DeviceVariableContext ctx) {
        return pluginManager.hasLocalPluginDeviceVariable(ctx);
    }

    @Override
    public boolean isDeviceAvailable(DeviceContext ctx) {
        return isDeviceAvailable(ctx, System.currentTimeMillis());
    }

    @Override
    synchronized public io.netty.util.concurrent.Future publishDevice(final HobsonDeviceProxy device, final Map<String,Object> config, final Runnable runnable) {
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

                try {
                    // persist the device configuration
                    if (configManager != null && config != null && config.size() > 0) {
                        configManager.setDeviceConfigurationProperties(device.getContext(), config);
                    }

                    // post the device started event
                    if (eventManager != null) {
                        eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(System.currentTimeMillis(), device.getContext()));
                    }

                    if (runnable != null) {
                        runnable.run();
                    }
                } catch (NotSerializableException e) {
                    logger.error("Unable to save configuration for device " + device.getContext() + ": " + config, e);
                }
            }
        });
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext dctx, String name, Object value) {
        try {
            configManager.setDeviceConfigurationProperty(dctx, name, value);

            // send update event
            eventManager.postEvent(
                dctx.getHubContext(),
                new DeviceConfigurationUpdateEvent(
                    System.currentTimeMillis(),
                    dctx,
                    configManager.getDeviceConfiguration(dctx)
                )
            );
        } catch (NotSerializableException e) {
            throw new HobsonRuntimeException("Unable to set device configuration property for " + dctx + ": \"" + name + "\"=\"" + value + "\"", e);
        }
    }

    @Override
    public void setDeviceConfiguration(DeviceContext dctx, Map<String, Object> values) {
        setDeviceConfigurationProperties(dctx, values, true);
    }

    @Override
    public void setDeviceName(DeviceContext dctx, String name) {
        deviceStore.setDeviceName(dctx, name);
    }

    @Override
    public void setDeviceTags(DeviceContext deviceContext, Set<String> set) {
        if (deviceStore != null) {
            deviceStore.setDeviceTags(deviceContext, set);
        }
    }

    @Override
    public void updateDeviceVariables(Collection<DeviceVariableDescriptor> vars) {
        if (deviceStore != null) {
            for (DeviceVariableDescriptor dvd : vars) {
                deviceStore.saveDeviceVariable(dvd);
            }
        }
    }

    public void setConfigManager(ConfigurationManager configManager) {
        this.configManager = configManager;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void setExecutorManager(ExecutorManager executorManager) {
        this.executorManager = executorManager;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public void setDeviceStore(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    boolean isDeviceAvailable(DeviceContext ctx, long now) {
        Long lastCheckin = getDeviceLastCheckin(ctx);
        return (lastCheckin != null && now - lastCheckin < AVAILABILITY_TIMEOUT_INTERVAL);
    }

    protected String getDeviceName(DeviceContext ctx) {
        return deviceStore.getDeviceName(ctx);
    }

    private void setDeviceConfigurationProperties(DeviceContext dctx, Map<String,Object> values, boolean sendEvent) {
        try {
            getDevice(dctx).getConfigurationClass().validate(values);

            configManager.setDeviceConfigurationProperties(dctx, values);

            // send update event
            if (sendEvent) {
                eventManager.postEvent(
                        dctx.getHubContext(),
                        new DeviceConfigurationUpdateEvent(
                                System.currentTimeMillis(),
                                dctx,
                                values
                        )
                );
            }
        } catch (NotSerializableException e) {
            throw new HobsonRuntimeException("Unable to set device configuration for " + dctx + ": " + values, e);
        }
    }
}
