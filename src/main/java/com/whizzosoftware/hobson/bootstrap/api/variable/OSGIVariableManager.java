/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.util.VariableChangeIdHelper;
import com.whizzosoftware.hobson.api.variable.*;
import org.osgi.framework.*;
import org.osgi.service.cm.ConfigurationAdmin;
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
    private volatile ConfigurationAdmin configAdmin;

    private static final String GLOBAL_NAME = "$GLOBAL$";

    private final Map<String,List<VariableRegistration>> variableRegistrations = new HashMap<>();

    @Override
    public void publishGlobalVariable(PluginContext ctx, String name, Object value, HobsonVariable.Mask mask) {
        publishDeviceVariable(DeviceContext.createLocal(ctx.getPluginId(), GLOBAL_NAME), name, value, mask);
    }

    @Override
    public Collection<HobsonVariable> getAllVariables(String userId, String hubId) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    results.add((HobsonVariable)bundleContext.getService(ref));
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving variables", e);
        }
        return results;
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
    public void publishDeviceVariable(DeviceContext ctx, String name, Object value, HobsonVariable.Mask mask) {
        // make sure the variable name is legal
        if (name == null || name.contains(",") || name.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish variable \"" + name + "\": name is either null or contains an invalid character");
        }

        // make sure variable doesn't already exist
        if (hasDeviceVariable(ctx.getUserId(), ctx.getHubId(), ctx.getPluginId(), ctx.getDeviceId(), name)) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + ctx.getPluginId() + "," + ctx.getDeviceId() + "," + name);
        }

        // publish the variable
        Dictionary<String,String> props = new Hashtable<>();
        props.put("pluginId", ctx.getPluginId());
        props.put("deviceId", ctx.getDeviceId());
        props.put("name", name);
        addVariableRegistration(ctx.getPluginId(), ctx.getDeviceId(), name, getBundleContext().registerService(
            HobsonVariable.class.getName(),
            new HobsonVariableImpl(ctx.getPluginId(), ctx.getDeviceId(), name, value, mask),
            props
        ));
    }

    @Override
    public void unpublishGlobalVariable(PluginContext ctx, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && reg.getName().equals(name)) {
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
    public void unpublishAllPluginVariables(PluginContext ctx) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                for (VariableRegistration vr : regs) {
                    vr.unregister();
                }
                variableRegistrations.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public Long setGlobalVariable(PluginContext ctx, String name, Object value) {
        // TODO
        return null;
    }

    @Override
    public Map<String, Long> setGlobalVariables(PluginContext ctx, Map<String, Object> values) {
        // TODO
        return null;
    }

    @Override
    public void unpublishDeviceVariable(DeviceContext ctx, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && reg.getDeviceId().equals(ctx.getDeviceId()) && reg.getName().equals(name)) {
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
    public void unpublishAllDeviceVariables(DeviceContext ctx) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && reg.getDeviceId().equals(ctx.getDeviceId())) {
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
                ServiceReference[] refs = bundleContext.getServiceReferences((String)null, "(&(objectClass=" +
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
                ServiceReference[] refs = bundleContext.getServiceReferences((String)null, "(&(objectClass=" +
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
    public Long setDeviceVariable(DeviceContext ctx, String name, Object value) {
        HobsonVariable variable = getDeviceVariable(ctx.getUserId(), ctx.getHubId(), ctx.getPluginId(), ctx.getDeviceId(), name);
        if (variable == null) {
            throw new HobsonNotFoundException("Attempt to set unknown variable: " + ctx.getPluginId() + "." + ctx.getDeviceId() + "." + name);
        }
        Long lastUpdate = variable.getLastUpdate();
        logger.debug("Attempting to set variable {}.{}.{} to value {}", ctx.getPluginId(), ctx.getDeviceId(), name, value);
        eventManager.postEvent(ctx.getUserId(), ctx.getHubId(), new VariableUpdateRequestEvent(new VariableUpdate(ctx.getPluginId(), ctx.getDeviceId(), name, value)));
        return lastUpdate;
    }

    @Override
    public Map<String, Long> setDeviceVariables(DeviceContext ctx, Map<String, Object> values) {
        Map<String,Long> results = new HashMap<>();
        List<VariableUpdate> updates = new ArrayList<>();
        for (String name : values.keySet()) {
            HobsonVariable variable = getDeviceVariable(ctx.getUserId(), ctx.getHubId(), ctx.getPluginId(), ctx.getDeviceId(), name);
            if (variable != null) {
                results.put(name, variable.getLastUpdate());
            }
        }
        eventManager.postEvent(ctx.getUserId(), ctx.getHubId(), new VariableUpdateRequestEvent(updates));
        return results;
    }

    @Override
    public void confirmVariableUpdates(String userId, String hubId, List<VariableUpdate> updates) {
        HobsonVariable var;

        for (VariableUpdate update : updates) {
            if (update.isGlobal()) {
                var = getDeviceVariable(userId, hubId, update.getPluginId(), GLOBAL_NAME, update.getName());
            } else {
                var = getDeviceVariable(userId, hubId, update.getPluginId(), update.getDeviceId(), update.getName());
            }

            if (var != null) {
                ((HobsonVariableImpl)var).setValue(update.getValue());
            }
        }

        eventManager.postEvent(userId, hubId, new VariableUpdateNotificationEvent(updates));
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