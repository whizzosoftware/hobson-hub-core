/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.util.VariableChangeIdHelper;
import com.whizzosoftware.hobson.api.variable.*;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A VariableManager implementation that publishes variables as OSGi services and uses OSGi events to
 * change them.
 *
 * @author Dan Noguerol
 */
public class OSGIVariableManager implements VariableManager {
    private static final Logger logger = LoggerFactory.getLogger(OSGIVariableManager.class);

    private volatile EventManager eventManager;

    private static final String GLOBAL_NAME = "$GLOBAL$";

    private final Map<String,List<VariableRegistration>> variableRegistrations = new HashMap<>();

    @Override
    public void publishGlobalVariable(String userId, String hubId, String pluginId, HobsonVariable var) {
        publishDeviceVariable(userId, hubId, pluginId, GLOBAL_NAME, var);
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(String userId, String hubId) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    results.add((HobsonVariable)bundleContext.getService(ref));
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving global variables", e);
        }
        return results;
    }

    @Override
    public HobsonVariable getGlobalVariable(String userId, String hubId, String name) {
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + ")(name=" + name + "))");
            if (references != null && references.length > 0) {
                if (references.length > 1) {
                    throw new HobsonRuntimeException("Found multiple global variables for " + name + "]");
                } else {
                    return (HobsonVariable)bundleContext.getService(references[0]);
                }
            } else {
                throw new GlobalVariableNotFoundException(name);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving global variables", e);
        }
    }

    @Override
    public void publishDeviceVariable(String userId, String hubId, String pluginId, String deviceId, HobsonVariable var) {
        // make sure variable doesn't already exist
        if (hasDeviceVariable(userId, hubId, pluginId, deviceId, var.getName())) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + pluginId + "," + deviceId + "," + var.getName());
        }

        // publish the variable
        Properties props = new Properties();
        props.setProperty("pluginId", pluginId);
        props.setProperty("deviceId", deviceId);
        props.setProperty("name", var.getName());
        addVariableRegistration(pluginId, deviceId, var.getName(), getBundleContext().registerService(
                HobsonVariable.class.getName(),
                var,
                props
        ));
    }

    @Override
    public void unpublishGlobalVariable(String userId, String hubId, String pluginId, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getName().equals(name)) {
                        vr = reg;
                        break;
                    }
                }
                if (vr != null) {
                    vr.unregister();
                    regs.remove(vr);
                }
            }
        }
    }

    @Override
    public void unpublishAllPluginVariables(String userId, String hubId, String pluginId) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                for (VariableRegistration vr : regs) {
                    vr.unregister();
                }
                variableRegistrations.remove(pluginId);
            }
        }
    }

    @Override
    public void unpublishDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getDeviceId().equals(deviceId) && reg.getName().equals(name)) {
                        vr = reg;
                        break;
                    }
                }
                if (vr != null) {
                    vr.unregister();
                    regs.remove(vr);
                }
            }
        }
    }

    @Override
    public void unpublishAllDeviceVariables(String userId, String hubId, String pluginId, String deviceId) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getDeviceId().equals(deviceId)) {
                        vr = reg;
                        break;
                    }
                }
                if (vr != null) {
                    vr.unregister();
                    regs.remove(vr);
                }
            }
        }
    }

    @Override
    public Collection<HobsonVariable> getDeviceVariables(String userId, String hubId, String pluginId, String deviceId) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(pluginId=" + pluginId + ")(deviceId=" + deviceId + "))");
                if (refs != null) {
                    List<HobsonVariable> results = new ArrayList<>();
                    for (ServiceReference ref : refs) {
                        results.add((HobsonVariable)bundleContext.getService(ref));
                    }
                    return results;
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variables for device ID \"" + deviceId + "\"", e);
            }
        }
        return null;
    }

    @Override
    public Collection<String> getDeviceVariableChangeIds(String userId, String hubId, String pluginId, String deviceId) {
        List<String> eventIds = new ArrayList<>();

        Collection<HobsonVariable> deviceVars = getDeviceVariables(userId, hubId, pluginId, deviceId);
        for (HobsonVariable v : deviceVars) {
            VariableChangeIdHelper.populateChangeIdsForVariableName(v.getName(), eventIds);
        }

        return eventIds;
    }

    @Override
    public HobsonVariable getDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(null, "(&(objectClass=" +
                    HobsonVariable.class.getName() +
                    ")(pluginId=" +
                    pluginId +
                    ")(deviceId=" +
                    deviceId +
                    ")(name=" +
                    name +
                    "))"
                );
                if (refs != null && refs.length == 1) {
                    return (HobsonVariable) bundleContext.getService(refs[0]);
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + pluginId + "." + deviceId + "[" + name + "]");
                } else {
                    throw new DeviceVariableNotFoundException(pluginId, deviceId, name);
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + pluginId + "." + deviceId + "[" + name + "]", e);
            }
        }
        return null;
    }

    @Override
    public boolean hasDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(null, "(&(objectClass=" +
                                HobsonVariable.class.getName() +
                                ")(pluginId=" +
                                pluginId +
                                ")(deviceId=" +
                                deviceId +
                                ")(name=" +
                                name +
                                "))"
                );
                if (refs != null && refs.length == 1) {
                    return true;
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + pluginId + "." + deviceId + "[" + name + "]");
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + pluginId + "." + deviceId + "[" + name + "]", e);
            }
        }
        return false;
    }

    @Override
    public Long setDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name, Object value) {
        HobsonVariable variable = getDeviceVariable(userId, hubId, pluginId, deviceId, name);
        Long lastUpdate = variable.getLastUpdate();
        eventManager.postEvent(userId, hubId, new VariableUpdateRequestEvent(pluginId, deviceId, name, value));
        return lastUpdate;
    }

    @Override
    public void fireVariableUpdateNotification(final String userId, final String hubId, HobsonPlugin plugin, final VariableUpdate update) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    setVariable(userId, hubId, update, true);
                } catch (DeviceVariableNotFoundException e) {
                    logger.error("Attempt to update a variable that has not been published: {}", update);
                }
            }
        });
    }

    @Override
    public void fireVariableUpdateNotifications(final String userId, final String hubId, HobsonPlugin plugin, final List<VariableUpdate> updates) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    for (VariableUpdate update : updates) {
                        setVariable(userId, hubId, update, false);
                    }
                    eventManager.postEvent(userId, hubId, new VariableUpdateNotificationEvent(updates));
                } catch (DeviceVariableNotFoundException e) {
                    logger.error("Attempt to update a variable that has not been published: {}", updates);
                }
            }
        });
    }

    protected void setVariable(String userId, String hubId, VariableUpdate update, boolean generateEvent) {
        HobsonVariable var;

        // if the device ID is null, it's a global variable
        if (update.getDeviceId() == null) {
            var = getDeviceVariable(userId, hubId, update.getPluginId(), GLOBAL_NAME, update.getName());
        } else {
            var = getDeviceVariable(userId, hubId, update.getPluginId(), update.getDeviceId(), update.getName());
        }

        if (var != null) {
            ((HobsonVariableImpl)var).setValue(update.getValue());
        }
        if (generateEvent) {
            eventManager.postEvent(userId, hubId, new VariableUpdateNotificationEvent(update));
        }
    }

    protected BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if (bundle != null) {
            return bundle.getBundleContext();
        } else {
            return null;
        }
    }

    protected void addVariableRegistration(String pluginId, String deviceId, String name, ServiceRegistration reg) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs == null) {
                regs = new ArrayList<>();
                variableRegistrations.put(pluginId, regs);
            }
            regs.add(new VariableRegistration(pluginId, deviceId, name, reg));
        }
    }

    private class VariableRegistration {
        private String pluginId;
        private String deviceId;
        private String name;
        private ServiceRegistration registration;

        public VariableRegistration(String pluginId, String deviceId, String name, ServiceRegistration registration) {
            this.pluginId = pluginId;
            this.deviceId = deviceId;
            this.name = name;
            this.registration = registration;
        }

        public String getPluginId() {
            return pluginId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getName() {
            return name;
        }

        public void unregister() {
            registration.unregister();
        }
    }
}