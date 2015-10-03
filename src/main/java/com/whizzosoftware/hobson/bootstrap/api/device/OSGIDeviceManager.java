/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.device.store.DeviceBootstrapStore;
import com.whizzosoftware.hobson.api.event.DeviceConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.DeviceStoppedEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.EventLoopExecutor;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.device.store.MapDBDeviceBootstrapStore;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.bootstrap.util.DictionaryUtil;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * An OSGi implementation of DeviceManager.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceManager implements DeviceManager, ServiceListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String DEVICE_PID_SEPARATOR = ".";

    volatile private BundleContext bundleContext;
    volatile private EventManager eventManager;
    volatile private ConfigurationAdmin configAdmin;
    volatile private VariableManager variableManager;
    volatile private PluginManager pluginManager;

    private final Map<String,List<DeviceServiceRegistration>> serviceRegistrations = new HashMap<>();
    private DeviceBootstrapStore bootstrapStore;
    private DeviceAvailabilityMonitor deviceAvailabilityMonitor;
    private ScheduledExecutorService deviceAvailabilityExecutor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Device Availability Monitor");
        }
    });
    private ScheduledFuture deviceAvailabilityFuture;

    public void start() {
        // if a bootstrap store hasn't already been injected, create a default one
        if (bootstrapStore == null) {
            this.bootstrapStore = new MapDBDeviceBootstrapStore(
                pluginManager.getDataFile(
                        PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                        "bootstraps"
                )
            );
        }

        try {
            bundleContext.addServiceListener(this, "(objectclass=" + HobsonDevice.class.getName() + ")");
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error adding listener for device registrations");
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
        bundleContext.removeServiceListener(this);

        // stop the device availability monitor
        if (deviceAvailabilityFuture != null) {
            deviceAvailabilityFuture.cancel(true);
        }
    }

    @Override
    public DeviceBootstrap createDeviceBootstrap(HubContext hubContext, String deviceId) {
        // make sure a bootstrap hasn't already been created for this device ID
        if (bootstrapStore.hasBootstrapForDeviceId(hubContext, deviceId)) {
            return null;
        }

        // create the device bootstrap
        DeviceBootstrap db = new DeviceBootstrap(UUID.randomUUID().toString(), deviceId, System.currentTimeMillis());
        db.setSecret(UUID.randomUUID().toString());
        bootstrapStore.saveBootstrap(hubContext, db);
        return db;
    }

    @Override
    public Collection<DeviceBootstrap> getDeviceBootstraps(HubContext hubContext) {
        List<DeviceBootstrap> results = new ArrayList<>();
        for (DeviceBootstrap db : bootstrapStore.getAllBootstraps(hubContext)) {
            results.add(db);
        }
        return results;
    }

    @Override
    public DeviceBootstrap getDeviceBootstrap(HubContext hubContext, String id) {
        return bootstrapStore.getBootstrap(hubContext, id);
    }

    @Override
    public DeviceBootstrap registerDeviceBootstrap(HubContext hubContext, String deviceId) {
        DeviceBootstrap bootstrap = bootstrapStore.getBoostrapForDeviceId(hubContext, deviceId);
        if (bootstrap != null) {
            if (bootstrap.getBootstrapTime() == null) {
                bootstrap.setBootstrapTime(System.currentTimeMillis());
                bootstrapStore.saveBootstrap(hubContext, bootstrap);
                return bootstrap;
            } else {
                throw new DeviceAlreadyBoostrappedException(bootstrap.getId());
            }
        } else {
            throw new DeviceBootstrapNotFoundException();
        }
    }

    @Override
    public void deleteDeviceBootstrap(HubContext hubContext, String id) {
        bootstrapStore.deleteBootstrap(hubContext, id);
    }

    @Override
    public boolean verifyDeviceBootstrap(HubContext hubContext, String id, String secret) {
        DeviceBootstrap db = bootstrapStore.getBootstrap(hubContext, id);
        return (db != null && secret.equals(db.getSecret()));
    }

    @Override
    public void resetDeviceBootstrap(HubContext hubContext, String id) {
        DeviceBootstrap db = bootstrapStore.getBootstrap(hubContext, id);
        if (db != null) {
            db.setBootstrapTime(null);
            bootstrapStore.saveBootstrap(hubContext, db);
        }
    }

    @Override
    public Collection<HobsonDevice> getAllDevices(HubContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<HobsonDevice> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(HobsonDevice.class.getName(), null);
            if (references != null) {
                for (ServiceReference ref : references) {
                    results.add((HobsonDevice)context.getService(ref));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving devices", e);
        }
    }

    @Override
    public Collection<HobsonDevice> getAllDevices(PluginContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<HobsonDevice> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + ctx.getPluginId() + "))");
            if (references != null) {
                for (ServiceReference ref : references) {
                    results.add((HobsonDevice)context.getService(ref));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving device", e);
        }
    }

    @Override
    public HobsonDevice getDevice(DeviceContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(deviceId=" + ctx.getDeviceId() + "))");
            if (references != null && references.length == 1) {
                return (HobsonDevice)context.getService(references[0]);
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate devices detected");
            } else {
                throw new DeviceNotFoundException(ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving device", e);
        }
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx) {
        org.osgi.service.cm.Configuration config = getOSGIDeviceConfiguration(ctx);
        Dictionary props = config.getProperties();

        // build a list of PropertyContainer objects
        HobsonDevice device = getDevice(ctx);
        PropertyContainerClass metas = device.getConfigurationClass();

        PropertyContainer ci = new PropertyContainer();

        for (TypedProperty meta : metas.getSupportedProperties()) {

            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }

            // if the name property is null, use the default device name
            if ("name".equals(meta.getId()) && value == null) {
                value = device.getName();
            }

            ci.setPropertyValue(meta.getId(), value);
        }

        return ci;
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        try {
            for (Bundle bundle : bundleContext.getBundles()) {
                if (ctx.getPluginId().equals(bundle.getSymbolicName())) {
                    if (configAdmin != null) {
                        org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(ctx.getPluginId() + "." + ctx.getDeviceId(), bundle.getLocation());
                        Dictionary dic = config.getProperties();
                        if (dic != null) {
                            return dic.get(name);
                        }
                    } else {
                        throw new ConfigurationException("Unable to get device configuration property: ConfigurationAdmin service is not available");
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new ConfigurationException("Error obtaining configuration", e);
        }
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(deviceId=" + ctx.getDeviceId() + "))");
            if (references != null && references.length == 1) {
                return true;
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate devices detected");
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving device", e);
        }
        return false;
    }

    @Override
    public void publishDevice(HobsonDevice device) {
        publishDevice(device, false);
    }

    @Override
    synchronized public void publishDevice(HobsonDevice device, boolean republish) {
        BundleContext context = BundleUtil.getBundleContext(getClass(), device.getContext().getPluginId());

        String pluginId = device.getContext().getPluginId();
        String deviceId = device.getContext().getDeviceId();

        // check that the device ID is legal
        if (deviceId == null || deviceId.contains(",") || deviceId.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish device \"" + deviceId + "\": the ID is either null or contains an invalid character");
        }

        // check if the device already exists
        if (hasDevice(device.getContext())) {
            // if it does and it's an explicit re-publish, remove the current device
            if (republish) {
                logger.debug("Removing existing device: {}", device.getContext());
                removeDeviceRegistration(pluginId, deviceId);
                // if it does and it's not an explicit re-publish, throw an exception
            } else {
                throw new HobsonRuntimeException("Attempt to publish a duplicate device: " + device.getContext());
            }
        }

        if (context != null) {
            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            props.put("deviceId", deviceId);

            logger.debug("Registering new device: {}", device.getContext());

            ServiceRegistration deviceReg = context.registerService(
                    HobsonDevice.class.getName(),
                    device,
                    props
            );
            addDeviceRegistration(pluginId, deviceReg);
        }
    }

    @Override
    public void checkInDevice(DeviceContext ctx, Long checkInTime) {
        HobsonDevice device = getDevice(ctx);
        if (device != null && device.getRuntime() != null) {
            device.getRuntime().checkInDevice(checkInTime);
        }
    }

    @Override
    synchronized public void unpublishDevice(final DeviceContext ctx, final EventLoopExecutor executor) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(ctx.getPluginId());
        if (regs != null) {
            DeviceServiceRegistration dsr = null;
            try {
                final long now = System.currentTimeMillis();
                for (final DeviceServiceRegistration reg : regs) {
                    final HobsonDevice device = reg.getDevice();
                    if (device != null && device.getContext().getDeviceId().equals(ctx.getDeviceId())) {
                        dsr = reg;
                        // execute the device's shutdown method using its plugin event loop
                        executor.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.getRuntime().onShutdown();
                                eventManager.postEvent(ctx.getPluginContext().getHubContext(), new DeviceStoppedEvent(now, reg.getDevice()));
                            }
                        });
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error stopping device: " + ctx, e);
            } finally {
                if (dsr != null) {
                    dsr.unregister();
                    regs.remove(dsr);
                }
            }
        }
    }

    @Override
    synchronized public void unpublishAllDevices(final PluginContext ctx, final EventLoopExecutor executor) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(ctx.getPluginId());
        if (regs != null) {
            try {
                final long now = System.currentTimeMillis();
                for (DeviceServiceRegistration reg : regs) {
                    try {
                        final HobsonDevice device = reg.getDevice();
                        reg.unregister();
                        // execute the device's shutdown method using its plugin event loop
                        executor.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.getRuntime().onShutdown();
                                eventManager.postEvent(ctx.getHubContext(), new DeviceStoppedEvent(now, device));
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Error stopping device for " + ctx, e);
                    }
                }
            } finally {
                serviceRegistrations.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public void setDeviceConfiguration(DeviceContext ctx, PropertyContainer config) {
        // TODO
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext ctx, String name, Object value, boolean overwrite) {
        setDeviceConfigurationProperties(ctx, Collections.singletonMap(name, value), overwrite);
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, Map<String,Object> values, boolean overwrite) {
        try {
            if (configAdmin != null) {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());

                if (bundle != null) {
                    // get configuration
                    org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(ctx.getPluginId() + "." + ctx.getDeviceId(), bundle.getLocation());

                    // update configuration dictionary
                    DictionaryUtil.updateConfigurationDictionary(config, values, overwrite);

                    // send update event
                    eventManager.postEvent(
                        ctx.getPluginContext().getHubContext(),
                        new DeviceConfigurationUpdateEvent(
                            System.currentTimeMillis(),
                            ctx.getPluginId(),
                            ctx.getDeviceId(),
                            getDeviceConfiguration(ctx)
                        )
                    );
                } else {
                    throw new ConfigurationException("Unable to set device configuration: no bundle found for " + ctx.getPluginId());
                }
            } else {
                throw new ConfigurationException("Unable to set device configuration: ConfigurationAdmin service is not available");
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error obtaining configuration", e);
        }
    }

    @Override
    public void setDeviceName(DeviceContext ctx, String name) {
        setDeviceConfigurationProperty(ctx, "name", name, true);
    }

    synchronized private void addDeviceRegistration(String pluginId, ServiceRegistration deviceRegistration) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(pluginId);
        if (regs == null) {
            regs = new ArrayList<>();
            serviceRegistrations.put(pluginId, regs);
        }
        regs.add(new DeviceServiceRegistration(deviceRegistration));
    }

    synchronized private void removeDeviceRegistration(String pluginId, String deviceId) {
        DeviceServiceRegistration dr = null;
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(pluginId);
        if (regs != null) {
            for (DeviceServiceRegistration r : regs) {
                if (r.getDevice().getContext().getDeviceId().equals(deviceId)) {
                    dr = r;
                    break;
                }
            }
            if (dr != null) {
                dr.unregister();
                regs.remove(dr);
            }
        }
    }

    private String getDevicePID(String pluginId, String deviceId) {
        return pluginId + DEVICE_PID_SEPARATOR + deviceId;
    }

    private org.osgi.service.cm.Configuration getOSGIDeviceConfiguration(DeviceContext ctx) {
        if (bundleContext != null) {
            try {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(ctx.getPluginId());
                if (bundle != null) {
                    org.osgi.service.cm.Configuration c = configAdmin.getConfiguration(getDevicePID(ctx.getPluginId(), ctx.getDeviceId()), bundle.getLocation());
                    if (c == null) {
                        throw new ConfigurationException("Unable to obtain configuration for: " + ctx + " (null)");
                    }
                    return c;
                }
            } catch (IOException e) {
                throw new ConfigurationException("Unable to obtain configuration for: " + ctx, e);
            }
        }
        throw new ConfigurationException("Unable to obtain configuration for: " + ctx);
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent) {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            final HobsonDevice device = (HobsonDevice)bundleContext.getService(serviceEvent.getServiceReference());
            final HobsonPlugin plugin = pluginManager.getLocalPlugin(device.getContext().getPluginContext());

            logger.debug("Device {} registered", device.getContext().getDeviceId());

            // execute the device's startup method using its plugin event loop
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // invoke the device's onStartup() lifecycle callback
                    device.getRuntime().onStartup(getDeviceConfiguration(device.getContext()));

                    // post a device started event
                    eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(System.currentTimeMillis(), device));
                }
            });
        }
    }

    private class DeviceServiceRegistration {
        private ServiceRegistration deviceRegistration;

        public DeviceServiceRegistration(ServiceRegistration deviceRegistration) {
            this.deviceRegistration = deviceRegistration;
        }

        public HobsonDevice getDevice() {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference ref = deviceRegistration.getReference();
            return (HobsonDevice)context.getService(ref);
        }

        public void unregister() {
            deviceRegistration.unregister();
        }
    }
}
