/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
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
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An OSGi-specific implementation of a DeviceStore.
 *
 * @author Dan Noguerol
 */
public class OSGIDeviceStore implements DeviceStore, ServiceListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());


    private BundleContext bundleContext;
    private DeviceConfigurationStore deviceConfigStore;
    private EventManager eventManager;
    private PluginManager pluginManager;
    private final Map<String,List<DeviceServiceRegistration>> serviceRegistrations = new HashMap<>();

    public OSGIDeviceStore(BundleContext bundleContext, DeviceConfigurationStore deviceConfigStore, EventManager eventManager, PluginManager pluginManager) {
        this.bundleContext = bundleContext;
        this.deviceConfigStore = deviceConfigStore;
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
        deviceConfigStore.close();
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
        HobsonDevice device = getDevice(ctx);
        return deviceConfigStore.getDeviceConfiguration(ctx, device.getConfigurationClass(), device.getName());
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        return deviceConfigStore.getDeviceConfigurationProperty(ctx, name);
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
        HobsonDevice device = getDevice(ctx);
        PropertyContainerClass configurationClass = device.getConfigurationClass();
        String deviceName = device.getName();

        deviceConfigStore.setDeviceConfigurationProperties(ctx, configurationClass, device.getName(), values, overwrite);

        // send update event
        eventManager.postEvent(
            ctx.getPluginContext().getHubContext(),
            new DeviceConfigurationUpdateEvent(
                System.currentTimeMillis(),
                ctx.getPluginId(),
                ctx.getDeviceId(),
                deviceConfigStore.getDeviceConfiguration(ctx, configurationClass, deviceName)
            )
        );
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
