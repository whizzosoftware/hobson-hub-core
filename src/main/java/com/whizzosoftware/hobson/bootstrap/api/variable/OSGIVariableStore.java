/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.*;
import org.osgi.framework.*;

import java.util.*;

/**
 * OSGi implementation of VariableStore interface. The variables are published as OSGi services.
 *
 * @author Dan Noguerol
 */
public class OSGIVariableStore implements VariableStore {

    private final Map<String,List<VariableRegistration>> variableRegistrations = new HashMap<>();

    @Override
    public List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId) {
        return getVariables(ctx, pluginId, deviceId, null);
    }

    @Override
    public List<HobsonVariable> getVariables(HubContext ctx, String pluginId, String deviceId, String name) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(
                (String)null,
                createFilter(HobsonVariable.class.getName(), pluginId, deviceId, name)
            );
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
    public void publishVariable(HobsonVariable variable) {
        // publish the variable
        Dictionary<String,String> props = new Hashtable<>();
        props.put("pluginId", variable.getPluginId());
        props.put("deviceId", variable.getDeviceId());
        props.put("name", variable.getName());

        addVariableRegistration(
            variable.getPluginId(),
            variable.getDeviceId(),
            variable.getName(),
            getBundleContext().registerService(
                HobsonVariable.class.getName(),
                variable,
                props
            )
        );
    }

    @Override
    public void unpublishVariable(PluginContext ctx, String deviceId, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && (deviceId == null || reg.getDeviceId().equals(deviceId)) && reg.getName().equals(name)) {
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
    public void unpublishVariables(PluginContext ctx, String deviceId) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && (deviceId == null || reg.getDeviceId().equals(deviceId))) {
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

    protected String createFilter(String objectClass, String pluginId, String deviceId, String name) {
        StringBuilder sb = new StringBuilder("(&(objectClass=").append(objectClass).append(")");
        if (pluginId != null) {
            sb.append("(pluginId=").append(pluginId).append(")");
        }
        if (deviceId != null) {
            sb.append("(deviceId=").append(deviceId).append(")");
        }
        if (name != null) {
            sb.append("(name=").append(name).append(")");
        }
        sb.append(")");
        return sb.toString();
    }
}
