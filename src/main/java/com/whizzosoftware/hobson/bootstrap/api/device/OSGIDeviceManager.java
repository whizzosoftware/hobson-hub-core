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
import com.whizzosoftware.hobson.api.device.DeviceConfigurationListener;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.DeviceNotFoundException;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.DeviceStoppedEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.variable.DeviceVariableRef;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.api.variable.telemetry.TelemetryInterval;
import com.whizzosoftware.hobson.api.variable.telemetry.TemporalValue;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedService;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.rrd4j.ConsolFun.AVERAGE;

/**
 * An OSGi implementation of DeviceManager.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceManager implements DeviceManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String DEVICE_PID_SEPARATOR = ".";
    private static final String TELEMETRY_PID = "com.whizzosoftware.hobson.hub.telemetry";
    private static final String TELEMETRY_DELIMITER = ":";

    volatile private BundleContext bundleContext;
    volatile private EventManager eventManager;
    volatile private ConfigurationAdmin configAdmin;
    volatile private VariableManager variableManager;

    private final Map<String,List<DeviceServiceRegistration>> serviceRegistrations = new HashMap<>();
    private final Map<String,ServiceRegistration> managedServiceRegistrations = new HashMap<>();
    private final Map<String,Object> telemetryMutexes = new HashMap<>();

    @Override
    public Collection<HobsonDevice> getAllDevices(String userId, String hubId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<HobsonDevice> results = new ArrayList<HobsonDevice>();
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
    public Collection<HobsonDevice> getAllPluginDevices(String userId, String hubId, String pluginId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<HobsonDevice> results = new ArrayList<HobsonDevice>();
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + pluginId + "))");
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
    public HobsonDevice getDevice(String userId, String hubId, String pluginId, String deviceId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + pluginId + ")(deviceId=" + deviceId + "))");
            if (references != null && references.length == 1) {
                return (HobsonDevice)context.getService(references[0]);
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate devices detected");
            } else {
                throw new DeviceNotFoundException(pluginId, deviceId);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving device", e);
        }
    }

    @Override
    public boolean hasDevice(String userId, String hubId, String pluginId, String deviceId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + HobsonDevice.class.getName() + ")(pluginId=" + pluginId + ")(deviceId=" + deviceId + "))");
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
    public Configuration getDeviceConfiguration(String userId, String hubId, String pluginId, String deviceId) {
        org.osgi.service.cm.Configuration config = getOSGIDeviceConfiguration(pluginId, deviceId);
        Dictionary props = config.getProperties();

        // build a list of ConfigurationProperty objects
        HobsonDevice device = getDevice(userId, hubId, pluginId, deviceId);
        Collection<ConfigurationPropertyMetaData> metas = device.getConfigurationPropertyMetaData();

        List<ConfigurationProperty> properties = new ArrayList<>();

        for (ConfigurationPropertyMetaData meta : metas) {
            Object value = null;
            if (props != null) {
                value = props.get(meta.getId());
            }
            properties.add(new ConfigurationProperty(meta, value));
        }

        return new Configuration(properties);
    }

    @Override
    public void setDeviceConfigurationProperty(String userId, String hubId, String pluginId, String deviceId, String name, Object value, boolean overwrite) {
        try {
            for (Bundle bundle : bundleContext.getBundles()) {
                if (pluginId.equals(bundle.getSymbolicName())) {
                    if (configAdmin != null) {
                        org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(pluginId + "." + deviceId, bundle.getLocation());
                        Dictionary dic = config.getProperties();
                        if (dic == null) {
                            dic = new Hashtable();
                        }
                        if (dic.get(name) == null || overwrite) {
                            dic.put(name, value);
                            config.update(dic);
                        }
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
    public Object getDeviceConfigurationProperty(String userId, String hubId, String pluginId, String deviceId, String name) {
        try {
            for (Bundle bundle : bundleContext.getBundles()) {
                if (pluginId.equals(bundle.getSymbolicName())) {
                    if (configAdmin != null) {
                        org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(pluginId + "." + deviceId, bundle.getLocation());
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
    synchronized public void publishDevice(final String userId, final String hubId, final HobsonPlugin plugin, final HobsonDevice device) {
        BundleContext context = BundleUtil.getBundleContext(getClass(), device.getPluginId());

        // check that the device ID is legal
        if (device.getId() == null || device.getId().contains(",") || device.getId().contains(":")) {
            throw new HobsonRuntimeException("Unable to publish device \"" + device.getId() + "\": the ID is either null or contains an invalid character");
        }

        // check that the device doesn't already exist
        if (hasDevice(userId, hubId, device.getPluginId(), device.getId())) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate device: " + device.getPluginId() + "." + device.getId());
        }

        if (context != null) {
            // register device as a service
            Properties props = new Properties();
            props.setProperty("pluginId", device.getPluginId());
            props.setProperty("deviceId", device.getId());
            ServiceRegistration deviceReg = context.registerService(
                    HobsonDevice.class.getName(),
                    device,
                    props
            );
            addServiceRegistration(device.getPluginId(), deviceReg);

            // register to monitor device configuration (it's important to do this AFTER device service registration)
            registerForDeviceConfigurationUpdates(
                    userId,
                    hubId,
                    device.getPluginId(),
                    device.getId(),
                    new DeviceConfigurationListener() {
                        @Override
                        public void onDeviceConfigurationUpdate(final String deviceId, final Dictionary config) {
                            plugin.executeInEventLoop(new Runnable() {
                                @Override
                                public void run() {
                                    plugin.onDeviceConfigurationUpdate(deviceId, config);
                                }
                            });
                        }
                    }
            );

            logger.debug("Device {} registered", device.getId());

            // execute the device's startup method using its plugin event loop
            plugin.executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    device.onStartup();
                    eventManager.postEvent(userId, hubId, new DeviceStartedEvent(device));
                }
            });
        }
    }

    @Override
    synchronized public void unpublishAllDevices(final String userId, final String hubId, HobsonPlugin plugin) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(plugin.getId());
        if (regs != null) {
            try {
                for (DeviceServiceRegistration reg : regs) {
                    try {
                        final HobsonDevice device = reg.getDevice();
                        reg.unregister();
                        // execute the device's shutdown method using its plugin event loop
                        plugin.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.onShutdown();
                                eventManager.postEvent(userId, hubId, new DeviceStoppedEvent(device));
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Error stopping device for " + plugin.getId(), e);
                    }
                }
            } finally {
                serviceRegistrations.remove(plugin.getId());
            }
        }
    }

    @Override
    public void registerForDeviceConfigurationUpdates(String userId, String hubId, String pluginId, final String deviceId, final DeviceConfigurationListener listener) {
        synchronized (managedServiceRegistrations) {
            String devicePID = getDevicePID(pluginId, deviceId);

            Dictionary dic = new Hashtable();
            dic.put("service.pid", devicePID);
            dic.put("pluginId", pluginId);
            dic.put("deviceId", deviceId);
            BundleContext context = BundleUtil.getBundleForSymbolicName(pluginId).getBundleContext();
            managedServiceRegistrations.put(
                    devicePID,
                    context.registerService(ManagedService.class.getName(), new ManagedService() {
                        @Override
                        public void updated(Dictionary config) throws org.osgi.service.cm.ConfigurationException {
                            if (config == null) {
                                config = new Hashtable();
                            }
                            listener.onDeviceConfigurationUpdate(deviceId, config);
                        }
                    }, dic)
            );
        }
    }

    @Override
    public Collection<HobsonDevice> getAllTelemetryEnabledDevices(String userId, String hubId) {
        List<HobsonDevice> results = new ArrayList<HobsonDevice>();
        for (HobsonDevice device : getAllDevices(userId, hubId)) {
            if (isDeviceTelemetryEnabled(userId, hubId, device.getPluginId(), device.getId())) {
                results.add(device);
            }
        }
        return results;
    }

    @Override
    public boolean isDeviceTelemetryEnabled(String userId, String hubId, String pluginId, String deviceId) {
        Boolean b = (Boolean)getDeviceConfigurationProperty(userId, hubId, pluginId, deviceId, "telemetry");
        return (b != null && b);
    }

    @Override
    public void enableDeviceTelemetry(String userId, String hubId, String pluginId, String deviceId, boolean enabled) {
        setDeviceConfigurationProperty(userId, hubId, pluginId, deviceId, "telemetry", enabled, true);
    }

    @Override
    public Map<String, Collection<TemporalValue>> getDeviceTelemetry(String userId, String hubId, String pluginId, String deviceId, long endTime, TelemetryInterval interval) {
        Map<String,Collection<TemporalValue>> results = new HashMap<>();
        HobsonDevice device = getDevice(userId, hubId, pluginId, deviceId);
        String[] variables = device.getTelemetryVariableNames();
        if (variables != null) {
            for (String varName : variables) {
                results.put(varName, variableManager.getDeviceVariableTelemetry(userId, hubId, pluginId, deviceId, varName, endTime, interval));
            }
        }
        return results;
    }

    @Override
    public void writeDeviceTelemetry(String userId, String hubId, String pluginId, String deviceId, Map<String, TemporalValue> values) {
        for (String varName : values.keySet()) {
            TemporalValue value = values.get(varName);
            variableManager.writeDeviceVariableTelemetry(userId, hubId, pluginId, deviceId, varName, value.getValue(), value.getTime());
        }
    }

    @Override
    synchronized public void unpublishDevice(final String userId, final String hubId, HobsonPlugin plugin, String deviceId) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(plugin.getId());
        if (regs != null) {
            DeviceServiceRegistration dsr = null;
            try {
                for (final DeviceServiceRegistration reg : regs) {
                    final HobsonDevice device = reg.getDevice();
                    if (device != null && device.getId().equals(deviceId)) {
                        dsr = reg;
                        // execute the device's shutdown method using its plugin event loop
                        plugin.executeInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                device.onShutdown();
                                eventManager.postEvent(userId, hubId, new DeviceStoppedEvent(reg.getDevice()));
                            }
                        });
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error stopping device " + plugin.getId() + "." + deviceId, e);
            } finally {
                if (dsr != null) {
                    dsr.unregister();
                    regs.remove(dsr);
                }
            }
        }
    }

    @Override
    public void setDeviceName(String userId, String hubId, String pluginId, String deviceId, String name) {
        setDeviceConfigurationProperty(userId, hubId, pluginId, deviceId, "name", name, true);
    }

    synchronized private void addServiceRegistration(String pluginId, ServiceRegistration deviceRegistration) {
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(pluginId);
        if (regs == null) {
            regs = new ArrayList<>();
            serviceRegistrations.put(pluginId, regs);
        }
        regs.add(new DeviceServiceRegistration(deviceRegistration));
    }

    private String getDevicePID(String pluginId, String deviceId) {
        return pluginId + DEVICE_PID_SEPARATOR + deviceId;
    }

    private org.osgi.service.cm.Configuration getOSGIDeviceConfiguration(String pluginId, String deviceId) {
        if (bundleContext != null) {
            try {
                Bundle bundle = BundleUtil.getBundleForSymbolicName(pluginId);
                if (bundle != null) {
                    org.osgi.service.cm.Configuration c = configAdmin.getConfiguration(getDevicePID(pluginId, deviceId), bundle.getLocation());
                    if (c == null) {
                        throw new ConfigurationException("Unable to obtain configuration for " + pluginId + " (null)");
                    }
                    return c;
                }
            } catch (IOException e) {
                throw new ConfigurationException("Unable to obtain configuration for " + pluginId, e);
            }
        }
        throw new ConfigurationException("Unable to obtain configuration for " + pluginId);
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
