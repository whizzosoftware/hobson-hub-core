/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.device.proxy.DeviceProxy;
import com.whizzosoftware.hobson.api.device.proxy.DeviceProxyVariable;
import com.whizzosoftware.hobson.api.device.store.DevicePassportStore;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.HobsonPluginRuntime;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.api.variable.DeviceVariable;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescription;
import com.whizzosoftware.hobson.bootstrap.api.device.store.*;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
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

    volatile private EventManager eventManager;
    volatile private ConfigurationManager configManager;
    volatile private PluginManager pluginManager;

    private Map<DeviceContext,Boolean> deviceAvailabilityMap = Collections.synchronizedMap(new HashMap<DeviceContext,Boolean>());
    private Map<DeviceContext,Long> deviceCheckIns = Collections.synchronizedMap(new HashMap<DeviceContext,Long>());
    private DeviceStore deviceStore;
    private DevicePassportStore passportStore;
    private final Map<PluginContext,Map<DeviceType,PropertyContainerClass>> deviceTypeRegistry = new HashMap<>();
    private final Set<String> variableNameSet = new HashSet<>();
    private ScheduledExecutorService deviceAvailabilityExecutor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Device Availability Monitor");
        }
    });
    private ScheduledFuture deviceAvailabilityFuture;

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
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
        DeviceAvailabilityMonitor deviceAvailabilityMonitor = new DeviceAvailabilityMonitor(HubContext.createLocal(), this, eventManager);
        deviceAvailabilityFuture = deviceAvailabilityExecutor.scheduleAtFixedRate(
                deviceAvailabilityMonitor,
            Math.min(30, DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL),
            Math.min(30, DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL),
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
    public Future sendDeviceHint(final DeviceDescription device, final PropertyContainer config) {
        final HobsonPlugin plugin = pluginManager.getLocalPlugin(device.getContext().getPluginContext());
        final HobsonPluginRuntime runtime = plugin.getRuntime();
        return runtime.getEventLoopExecutor().executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                runtime.onDeviceHint(device.getName(), device.getDeviceType(), config);
            }
        });
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
    public Collection<DeviceDescription> getAllDeviceDescriptions(HubContext ctx) {
        return deviceStore.getAllDevices(ctx);
    }

    @Override
    public Collection<DeviceDescription> getAllDeviceDescriptions(PluginContext ctx) {
        return deviceStore.getAllDevices(ctx);
    }

    @Override
    public Collection<String> getDeviceVariableNames(HubContext hubContext) {
        return variableNameSet;
    }

    @Override
    public DeviceDescription getDeviceDescription(DeviceContext ctx) {
        return deviceStore.getDevice(ctx);
    }

    @Override
    public DeviceVariable getDeviceVariable(DeviceVariableContext vctx) {
        HobsonPlugin plugin = pluginManager.getLocalPlugin(vctx.getDeviceContext().getPluginContext());
        return plugin.getRuntime().getDeviceVariable(vctx.getDeviceId(), vctx.getName());
    }

    @Override
    public Collection<DeviceVariable> getDeviceVariables(DeviceContext dctx) {
        HobsonPlugin plugin = pluginManager.getLocalPlugin(dctx.getPluginContext());
        return plugin.getRuntime().getDeviceVariables(dctx.getDeviceId());
    }

    @Override
    public Future setDeviceVariable(final DeviceVariableContext ctx, final Object value) {
        final HobsonPlugin plugin = pluginManager.getLocalPlugin(ctx.getPluginContext());
        return plugin.getRuntime().submitInEventLoop(new Runnable() {
            @Override
            public void run() {
                plugin.getRuntime().onSetDeviceVariable(ctx.getDeviceId(), ctx.getName(), value);
            }
        });
    }

    @Override
    public Future setDeviceVariables(final Map<DeviceVariableContext,Object> map) {
        for (final DeviceVariableContext dvctx : map.keySet()) {
            setDeviceVariable(dvctx, map.get(dvctx)); // TODO: single setDeviceVariables call?
        }
        return null; // TODO
    }

    @Override
    public boolean hasDeviceVariableValue(DeviceVariableContext vctx) {
        final HobsonPlugin plugin = pluginManager.getLocalPlugin(vctx.getPluginContext());
        return plugin != null && plugin.getRuntime().hasDeviceVariableValue(vctx.getDeviceId(), vctx.getName());
    }

    @Override
    public Collection<DeviceType> getPluginDeviceTypes(PluginContext pctx) {
        synchronized (deviceTypeRegistry) {
            Map<DeviceType,PropertyContainerClass> map = deviceTypeRegistry.get(pctx);
            if (map != null) {
                return map.keySet();
            }
        }
        return null;
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx) {
        DeviceDescription device = deviceStore.getDevice(ctx);
        return configManager.getDeviceConfiguration(ctx, getDeviceConfigurationClass(device.getContext()));
    }

    @Override
    public PropertyContainerClass getDeviceTypeConfigurationClass(PluginContext pctx, DeviceType type) {
        synchronized (deviceTypeRegistry) {
            Map<DeviceType,PropertyContainerClass> map = deviceTypeRegistry.get(pctx);
            return map != null ? map.get(type) : null;
        }
    }

    @Override
    public PropertyContainerClass getDeviceConfigurationClass(DeviceContext dctx) {
        HobsonPlugin plugin = pluginManager.getLocalPlugin(dctx.getPluginContext());
        if (plugin != null) {
            return plugin.getRuntime().getDeviceConfigurationClass(dctx.getDeviceId());
        } else {
            return null;
        }
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        return configManager.getDeviceConfigurationProperty(ctx, name);
    }

    @Override
    public boolean isDeviceAvailable(DeviceContext ctx) {
        return isDeviceAvailable(ctx, System.currentTimeMillis());
    }

    @Override
    public void publishDeviceType(PluginContext pctx, DeviceType type, TypedProperty[] configProperties) {
        synchronized (deviceTypeRegistry) {
            Map<DeviceType,PropertyContainerClass> map = deviceTypeRegistry.get(pctx);
            if (map == null) {
                map = new HashMap<>();
                deviceTypeRegistry.put(pctx, map);
            }
            map.put(type, new PropertyContainerClass(PropertyContainerClassContext.create(pctx, "configuration"), "configuration", PropertyContainerClassType.DEVICE_CONFIG, null, Arrays.asList(configProperties)));
        }
    }

    boolean isDeviceAvailable(DeviceContext ctx, long now) {
        Long checkin = deviceCheckIns.get(ctx);
        boolean stale = (checkin == null || now - checkin >= DeviceProxy.AVAILABILITY_TIMEOUT_INTERVAL);
        Boolean avail = deviceAvailabilityMap.get(ctx);
        return (!stale && avail != null && avail);
    }

    @Override
    public Long getDeviceLastCheckIn(DeviceContext ctx) {
        return deviceCheckIns.get(ctx);
    }

    @Override
    public Set<String> getDeviceTags(DeviceContext deviceContext) {
        return null;
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return deviceStore.hasDevice(ctx);
    }

    @Override
    public Future publishDevice(PluginContext pctx, DeviceProxy proxy, Map<String,Object> config) {
        return publishDevice(
            pctx,
            proxy,
            new PropertyContainer(PropertyContainerClassContext.create(DeviceContext.create(pctx, proxy.getDeviceId()), "configuration"), config),
            System.currentTimeMillis()
        );
    }

    synchronized Future publishDevice(final PluginContext pctx, final DeviceProxy proxy, final PropertyContainer config, final long now) {
        final DeviceDescription device = createDeviceDescription(pctx, proxy, config != null ? config.getPropertyValues() : null);
        final String deviceId = device.getContext().getDeviceId();

        // check that the device ID is legal
        if (deviceId == null || deviceId.contains(",") || deviceId.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish device \"" + deviceId + "\": the ID is either null or contains an invalid character");
        }

        final HobsonPlugin plugin = pluginManager.getLocalPlugin(device.getContext().getPluginContext());

        return plugin.getRuntime().submitInEventLoop(new Runnable() {
            @Override
            public void run() {
                // invoke device's onStartup callback
                proxy.onStartup(config);

                // collect its list of variables
                for (DeviceVariable dv : proxy.getVariables()) {
                    if (!variableNameSet.contains(dv.getDescription().getName())) {
                        variableNameSet.add(dv.getDescription().getName());
                    }
                }

                // save the device description
                if (deviceStore != null) {
                    deviceStore.saveDevice(device);
                }

                if (eventManager != null) {
                    eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(System.currentTimeMillis(), device.getContext()));
                }
            }
        });
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext ctx, String name, Object value, boolean overwrite) {
        setDeviceConfigurationProperties(ctx, Collections.singletonMap(name, value), overwrite);
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, Map<String,Object> values, boolean overwrite) {
        setDeviceConfigurationProperties(deviceStore.getDevice(ctx), values, overwrite, true);
    }

    @Override
    public void setDeviceName(DeviceContext dctx, String name) {

    }

    protected void setDeviceConfigurationProperties(DeviceDescription desc, Map<String,Object> values, boolean overwrite, boolean sendEvent) {
        PropertyContainerClass configClass = getDeviceConfigurationClass(desc.getContext());
        configManager.setDeviceConfigurationProperties(desc.getContext(), configClass, desc.getName(), values, overwrite);

        // send update event
        if (sendEvent) {
            eventManager.postEvent(
                    desc.getContext().getPluginContext().getHubContext(),
                    new DeviceConfigurationUpdateEvent(
                        System.currentTimeMillis(),
                        desc.getContext().getPluginId(),
                        desc.getContext().getDeviceId(),
                        configManager.getDeviceConfiguration(desc.getContext(), configClass)
                    )
            );
        }
    }

    @Override
    public void setDeviceTags(DeviceContext deviceContext, Set<String> set) {
        throw new UnsupportedOperationException();
    }

    protected DeviceDescription createDeviceDescription(PluginContext pctx, DeviceProxy proxy, Map<String,Object> config) {
        DeviceDescription.Builder builder = new DeviceDescription.Builder(DeviceContext.create(pctx, proxy.getDeviceId())).
            name(config != null && config.containsKey("name") ? (String)config.get("name") : proxy.getDefaultName()).
            type(proxy.getDeviceType()).
            preferredVariableName(proxy.getPreferredVariableName()).
            manufacturerVersion(proxy.getManufacturerVersion()).
            manufacturerName(proxy.getManufacturerName()).
            modelName(proxy.getModelName());
        for (DeviceVariable dv : proxy.getVariables()) {
            builder.variableDescription(dv.getDescription());
        }
        return builder.build();
    }
}
