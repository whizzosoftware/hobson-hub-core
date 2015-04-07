/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.Configuration;
import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.config.ConfigurationProperty;
import com.whizzosoftware.hobson.api.config.ConfigurationPropertyMetaData;
import com.whizzosoftware.hobson.api.device.*;
import com.whizzosoftware.hobson.api.event.DeviceConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.DeviceStoppedEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.EventLoopExecutor;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.telemetry.TelemetryManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

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
    volatile private TelemetryManager telemetryManager;

    private final Map<String,List<DeviceServiceRegistration>> serviceRegistrations = new HashMap<>();
    private final Map<String,ServiceRegistration> managedServiceRegistrations = new HashMap<>();

    public void start() {
        try {
            bundleContext.addServiceListener(this, "(objectclass=" + HobsonDevice.class.getName() + ")");
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error adding listener for device registrations");
        }
    }

    public void stop() {
        bundleContext.removeServiceListener(this);
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
            List<HobsonDevice> results = new ArrayList<HobsonDevice>();
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
    public Collection<HobsonDevice> getAllTelemetryEnabledDevices(HubContext ctx) {
        List<HobsonDevice> results = new ArrayList<HobsonDevice>();
        for (HobsonDevice device : getAllDevices(ctx)) {
            if (isDeviceTelemetryEnabled(device.getContext())) {
                results.add(device);
            }
        }
        return results;
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
    public Configuration getDeviceConfiguration(DeviceContext ctx) {
        org.osgi.service.cm.Configuration config = getOSGIDeviceConfiguration(ctx);
        Dictionary props = config.getProperties();

        // build a list of ConfigurationProperty objects
        HobsonDevice device = getDevice(ctx);
        Collection<ConfigurationPropertyMetaData> metas = device.getConfigurationPropertyMetaData();

        Configuration ci = new Configuration();

        for (ConfigurationPropertyMetaData meta : metas) {
            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }

            // if the name property is null, use the default device name
            if ("name".equals(meta.getId()) && value == null) {
                value = device.getName();
            }

            ci.addProperty(new ConfigurationProperty(meta, value));
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
    public boolean isDeviceTelemetryEnabled(DeviceContext ctx) {
        Boolean b = (Boolean)getDeviceConfigurationProperty(ctx, "telemetry");
        return (b != null && b);
    }

    @Override
    synchronized public void unpublishDevice(final DeviceContext ctx, final EventLoopExecutor executor) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(ctx.getPluginId());
        if (regs != null) {
            DeviceServiceRegistration dsr = null;
            try {
                for (final DeviceServiceRegistration reg : regs) {
                    final HobsonDevice device = reg.getDevice();
                    if (device != null && device.getContext().getDeviceId().equals(ctx.getDeviceId())) {
                        dsr = reg;
                        // execute the device's shutdown method using its plugin event loop
                        executor.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.getRuntime().onShutdown();
                                eventManager.postEvent(ctx.getPluginContext().getHubContext(), new DeviceStoppedEvent(reg.getDevice()));
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
                for (DeviceServiceRegistration reg : regs) {
                    try {
                        final HobsonDevice device = reg.getDevice();
                        reg.unregister();
                        // execute the device's shutdown method using its plugin event loop
                        executor.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.getRuntime().onShutdown();
                                eventManager.postEvent(ctx.getHubContext(), new DeviceStoppedEvent(device));
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
    public void setDeviceConfiguration(DeviceContext ctx, Configuration config) {
        // TODO
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext ctx, String name, Object value, boolean overwrite) {
        setDeviceConfigurationProperties(ctx, Collections.singletonMap(name, value), overwrite);
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, Map<String,Object> values, boolean overwrite) {
        try {
            for (Bundle bundle : bundleContext.getBundles()) {
                if (ctx.getPluginId().equals(bundle.getSymbolicName())) {
                    if (configAdmin != null) {
                        org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(ctx.getPluginId() + "." + ctx.getDeviceId(), bundle.getLocation());
                        Dictionary dic = config.getProperties();
                        if (dic == null) {
                            dic = new Hashtable();
                        }
                        for (String name : values.keySet()) {
                            if (dic.get(name) == null || overwrite) {
                                dic.put(name, values.get(name));
                            }
                        }
                        config.update(dic);
                        eventManager.postEvent(ctx.getPluginContext().getHubContext(), new DeviceConfigurationUpdateEvent(ctx.getPluginId(), ctx.getDeviceId(), new Configuration(dic)));
                        return;
                    } else {
                        throw new ConfigurationException("Unable to set device name: ConfigurationAdmin service is not available");
                    }
                }
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
            final HobsonPlugin plugin = pluginManager.getPlugin(device.getContext().getPluginContext());

            logger.debug("Device {} registered", device.getContext().getDeviceId());

            // execute the device's startup method using its plugin event loop
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // invoke the device's onStartup() lifecycle callback
                    device.getRuntime().onStartup(getDeviceConfiguration(device.getContext()));

                    // post a device started event
                    eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(device));
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
