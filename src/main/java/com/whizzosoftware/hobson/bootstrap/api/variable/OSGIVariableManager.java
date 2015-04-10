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
import com.whizzosoftware.hobson.api.hub.HubContext;
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
        publishGlobalVariable(ctx, name, value, mask, null);
    }

    @Override
    public void publishGlobalVariable(PluginContext ctx, String name, Object value, HobsonVariable.Mask mask, String proxyType) {
        publishDeviceVariable(DeviceContext.create(ctx, GLOBAL_NAME), name, value, mask);
    }

    @Override
    public Collection<HobsonVariable> getAllVariables(HubContext ctx) {
        return getAllVariables(ctx, null);
    }

    @Override
    public Collection<HobsonVariable> getAllVariables(HubContext ctx, VariableProxyValueProvider proxyProvider) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    HobsonVariable v = (HobsonVariable)bundleContext.getService(ref);
                    if (v.hasProxyType() && proxyProvider != null) {
                        v = new HobsonVariableValueOverrider(v, proxyProvider.getProxyValue(v));
                    }
                    results.add(v);
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving variables", e);
        }
        return results;
    }

    @Override
    public HobsonVariable getDeviceVariable(DeviceContext ctx, String name) {
        return getDeviceVariable(ctx, name, null);
    }

    @Override
    public HobsonVariable getDeviceVariable(DeviceContext ctx, String name, VariableProxyValueProvider proxyProvider) {
        HobsonVariable v = null;
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences((String)null, "(&(objectClass=" +
                    HobsonVariable.class.getName() +
                    ")(pluginId=" +
                    ctx.getPluginId() +
                    ")(deviceId=" +
                    ctx.getDeviceId() +
                    ")(name=" +
                    name +
                    "))"
                );
                if (refs != null && refs.length == 1) {
                    v = (HobsonVariable)bundleContext.getService(refs[0]);
                    if (v.hasProxyType() && proxyProvider != null) {
                        v = new HobsonVariableValueOverrider(v, proxyProvider.getProxyValue(v));
                    }
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + ctx.getPluginId() + "." + ctx.getDeviceId() + "[" + name + "]");
                } else {
                    throw new DeviceVariableNotFoundException(ctx, name);
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + ctx + "[" + name + "]", e);
            }
        }
        return v;
    }

    @Override
    public Collection<String> getDeviceVariableChangeIds(DeviceContext ctx) {
        List<String> eventIds = new ArrayList<>();

        HobsonVariableCollection deviceVars = getDeviceVariables(ctx);
        for (HobsonVariable v : deviceVars.getCollection()) {
            VariableChangeIdHelper.populateChangeIdsForVariableName(v.getName(), eventIds);
        }

        return eventIds;
    }

    @Override
    public HobsonVariableCollection getDeviceVariables(DeviceContext ctx) {
        return getDeviceVariables(ctx, null);
    }

    @Override
    public HobsonVariableCollection getDeviceVariables(DeviceContext ctx, VariableProxyValueProvider proxyProvider) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(deviceId=" + ctx.getDeviceId() + "))");
                if (refs != null) {
                    HobsonVariableCollection results = new HobsonVariableCollection();
                    for (ServiceReference ref : refs) {
                        HobsonVariable v = (HobsonVariable)bundleContext.getService(ref);
                        if (v.hasProxyType() && proxyProvider != null) {
                            v = new HobsonVariableValueOverrider(v, proxyProvider.getProxyValue(v));
                        }
                        results.add(v);
                    }
                    return results;
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variables for device ID \"" + ctx.getDeviceId() + "\"", e);
            }
        }
        return null;
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(HubContext ctx) {
        return getGlobalVariables(ctx, null);
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(HubContext ctx, VariableProxyValueProvider proxyProvider) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    HobsonVariable v = (HobsonVariable)bundleContext.getService(ref);
                    if (v.hasProxyType() && proxyProvider != null) {
                        v = new HobsonVariableValueOverrider(v, proxyProvider.getProxyValue(v));
                    }
                    results.add(v);
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving global variables", e);
        }
        return results;
    }

    @Override
    public HobsonVariable getGlobalVariable(HubContext ctx, String name) {
        return getGlobalVariable(ctx, name, null);
    }

    @Override
    public HobsonVariable getGlobalVariable(HubContext ctx, String name, VariableProxyValueProvider proxyProvider) {
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + ")(name=" + name + "))");
            if (references != null && references.length > 0) {
                if (references.length > 1) {
                    throw new HobsonRuntimeException("Found multiple global variables for " + name + "]");
                } else {
                    HobsonVariable v = (HobsonVariable)bundleContext.getService(references[0]);
                    if (v.hasProxyType() && proxyProvider != null) {
                        v = new HobsonVariableValueOverrider(v, proxyProvider.getProxyValue(v));
                    }
                    return v;
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
        publishDeviceVariable(ctx, name, value, mask, null);
    }

    @Override
    public void publishDeviceVariable(DeviceContext ctx, String name, Object value, HobsonVariable.Mask mask, String proxyType) {
        // make sure the variable name is legal
        if (name == null || name.contains(",") || name.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish variable \"" + name + "\": name is either null or contains an invalid character");
        }

        // make sure variable doesn't already exist
        if (hasDeviceVariable(ctx, name)) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + ctx.getPluginId() + "," + ctx.getDeviceId() + "," + name);
        }

        // publish the variable
        Dictionary<String,String> props = new Hashtable<>();
        props.put("pluginId", ctx.getPluginId());
        props.put("deviceId", ctx.getDeviceId());
        props.put("name", name);

        HobsonVariableImpl v = new HobsonVariableImpl(ctx, name, value, mask, proxyType);
        logger.debug("Publishing device variable {} with value {}", v.toString(), value);
        addVariableRegistration(ctx.getPluginId(), ctx.getDeviceId(), name, getBundleContext().registerService(
                HobsonVariable.class.getName(),
                v,
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
    public boolean hasDeviceVariable(DeviceContext ctx, String name) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences((String)null, "(&(objectClass=" +
                    HobsonVariable.class.getName() +
                    ")(pluginId=" +
                    ctx.getPluginId() +
                    ")(deviceId=" +
                    ctx.getDeviceId() +
                    ")(name=" +
                    name +
                    "))"
                );
                if (refs != null && refs.length == 1) {
                    return true;
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + ctx.getPluginId() + "." + ctx.getDeviceId() + "[" + name + "]");
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + ctx.getPluginId() + "." + ctx.getDeviceId() + "[" + name + "]", e);
            }
        }
        return false;
    }

    @Override
    public Long setDeviceVariable(DeviceContext ctx, String name, Object value) {
        HobsonVariable variable = getDeviceVariable(ctx, name);
        if (variable == null) {
            throw new HobsonNotFoundException("Attempt to set unknown variable: " + ctx.getPluginId() + "." + ctx.getDeviceId() + "." + name);
        }
        Long lastUpdate = variable.getLastUpdate();
        logger.debug("Attempting to set variable {}.{}.{} to value {}", ctx.getPluginId(), ctx.getDeviceId(), name, value);
        eventManager.postEvent(ctx.getPluginContext().getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), new VariableUpdate(ctx, name, value)));
        return lastUpdate;
    }

    @Override
    public Map<String, Long> setDeviceVariables(DeviceContext ctx, Map<String, Object> values) {
        Map<String,Long> results = new HashMap<>();
        List<VariableUpdate> updates = new ArrayList<>();
        for (String name : values.keySet()) {
            HobsonVariable variable = getDeviceVariable(ctx, name);
            if (variable != null) {
                results.put(name, variable.getLastUpdate());
            }
        }
        eventManager.postEvent(ctx.getPluginContext().getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), updates));
        return results;
    }

    @Override
    public void applyVariableUpdates(HubContext ctx, List<VariableUpdate> updates) {
        HobsonVariable var;

        for (VariableUpdate update : updates) {
            if (update.isGlobal()) {
                var = getDeviceVariable(DeviceContext.create(ctx, update.getPluginId(), GLOBAL_NAME), update.getName());
            } else {
                var = getDeviceVariable(DeviceContext.create(ctx, update.getPluginId(), update.getDeviceId()), update.getName());
            }

            if (var != null) {
                logger.debug("Applying value for {}.{}.{}: {}", update.getPluginId(), update.getDeviceId(), update.getName(), update.getValue());
                ((HobsonVariableImpl)var).setValue(update.getValue());
            }
        }

        eventManager.postEvent(ctx, new VariableUpdateNotificationEvent(System.currentTimeMillis(), updates));
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