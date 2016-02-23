/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.ConfigurationException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceNotFoundException;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.event.DeviceConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.event.DeviceStartedEvent;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.bootstrap.util.DictionaryUtil;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * An OSGi-specific implementation of a DeviceStore.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceStore implements DeviceStore, ServiceListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String DEVICE_PID_SEPARATOR = ".";

    private BundleContext bundleContext;
    private ConfigurationAdmin configAdmin;
    private EventManager eventManager;
    private PluginManager pluginManager;
    private final Map<String,List<DeviceServiceRegistration>> serviceRegistrations = new HashMap<>();

    public OSGIDeviceStore(BundleContext bundleContext, ConfigurationAdmin configAdmin, EventManager eventManager, PluginManager pluginManager) {
        this.bundleContext = bundleContext;
        this.configAdmin = configAdmin;
        this.eventManager = eventManager;
        this.pluginManager = pluginManager;
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent) {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            final HobsonDevice device = (HobsonDevice)bundleContext.getService(serviceEvent.getServiceReference());
            final HobsonPlugin plugin = pluginManager.getLocalPlugin(device.getContext().getPluginContext());

            logger.trace("Detected a device registration: {}", device.getContext());

            // execute the device's startup method using its plugin event loop
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    try {
                        // invoke the device's onStartup() lifecycle callback
                        device.getRuntime().onStartup(getDeviceConfiguration(device.getContext()));

                        // post a device started event
                        eventManager.postEvent(device.getContext().getPluginContext().getHubContext(), new DeviceStartedEvent(System.currentTimeMillis(), device.getContext()));
                    } catch (Throwable t) {
                        logger.error("An uncaught exception occurred", t);
                    }
                }
            });
        }
    }

    @Override
    public void start() {
        logger.trace("Store is starting");
        try {
            bundleContext.addServiceListener(this, "(objectclass=" + HobsonDevice.class.getName() + ")");
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error adding listener for device registrations");
        }
    }

    @Override
    public void stop() {
        logger.trace("Store is stopping");
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

        ci.setContainerClassContext(metas.getContext());

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
    synchronized public void publishDevice(HobsonDevice device, boolean republish) {
        logger.trace("Attempting to publish device: {}", device.getContext());

        BundleContext context = BundleUtil.getBundleContext(getClass(), device.getContext().getPluginId());
        if (context != null) {
            String pluginId = device.getContext().getPluginId();
            String deviceId = device.getContext().getDeviceId();

            // check if the device already exists
            if (hasDevice(device.getContext())) {
                // if it does and it's an explicit re-publish, remove the current device
                if (republish) {
                    removeDeviceRegistration(pluginId, deviceId);
                    // if it does and it's not an explicit re-publish, throw an exception
                } else {
                    throw new HobsonRuntimeException("Attempt to publish a duplicate device: " + device.getContext());
                }
            }

            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            props.put("deviceId", deviceId);

            ServiceRegistration deviceReg = context.registerService(
                    HobsonDevice.class.getName(),
                    device,
                    props
            );
            addDeviceRegistration(pluginId, deviceReg);
        }

        logger.debug("Device published: {}", device.getContext());
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext ctx, Map<String, Object> values, boolean overwrite) {
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
    synchronized public void unpublishDevice(final DeviceContext ctx) {
        logger.trace("Attempting to unpublish device: {}", ctx);
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(ctx.getPluginId());
        if (regs != null) {
            for (DeviceServiceRegistration reg : regs) {
                HobsonDevice device = reg.getDevice();
                if (device != null && device.getContext().getDeviceId().equals(ctx.getDeviceId())) {
                    reg.unregister();
                    regs.remove(reg);
                    logger.debug("Device {} has shut down", ctx);
                    break;
                }
            }
        } else {
            logger.debug("Unable to find service registration for device: {}", ctx);
        }
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
        List<DeviceServiceRegistration> regs = serviceRegistrations.get(pluginId);
        if (regs != null) {
            for (Iterator<DeviceServiceRegistration> iterator = regs.iterator(); iterator.hasNext();) {
                DeviceServiceRegistration r = iterator.next();
                if (r.getDevice().getContext().getDeviceId().equals(deviceId)) {
                    r.unregister();
                    iterator.remove();
                    break;
                }
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
