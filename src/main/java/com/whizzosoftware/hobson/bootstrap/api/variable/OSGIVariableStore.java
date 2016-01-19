/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
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
    public Collection<HobsonVariable> getAllVariables(HubContext hctx) {
        return getVariables(VariableContext.create(hctx, null, null, null));
    }

    @Override
    public Collection<HobsonVariable> getDeviceVariables(DeviceContext ctx) {
        return getVariables(VariableContext.create(ctx, null));
    }

    @Override
    public Collection<HobsonVariable> getAllGlobalVariables(HubContext ctx) {
        return getVariables(VariableContext.createGlobal(PluginContext.create(ctx, null), null));
    }

    @Override
    public Collection<HobsonVariable> getPluginGlobalVariables(PluginContext ctx) {
        return getVariables(VariableContext.createGlobal(ctx, null));
    }

    @Override
    public boolean hasVariable(VariableContext ctx) {
        Collection<HobsonVariable> results = getVariables(ctx);
        return (results != null && results.size() > 0);
    }

    @Override
    public HobsonVariable getVariable(VariableContext ctx) {
        List<HobsonVariable> results = getVariables(ctx);
        if (results != null && results.size() > 0) {
            if (results.size() == 1) {
                return results.get(0);
            } else {
                throw new HobsonRuntimeException("Found multiple variables for " + ctx);
            }
        } else {
            throw new VariableNotFoundException(ctx.getDeviceContext(), ctx.getName());
        }
    }

    private List<HobsonVariable> getVariables(VariableContext ctx) {
        List<HobsonVariable> results = null;
        BundleContext bundleContext = getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(
                (String)null,
                createFilter(HobsonVariable.class.getName(), ctx)
            );
            if (references != null && references.length > 0) {
                results = new ArrayList<>();
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
    public Collection<String> getVariableNames() {
        List<String> names = new ArrayList<>();
        synchronized (variableRegistrations) {
            for (List<VariableRegistration> lreg : variableRegistrations.values()) {
                for (VariableRegistration ref : lreg) {
                    if (!names.contains(ref.getName())) {
                        names.add(ref.getName());
                    }
                }
            }
        }
        return names;
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
    public void unpublishVariable(VariableContext ctx) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(ctx.getPluginId()) && (!ctx.hasDeviceId() || reg.getDeviceId().equals(ctx.getDeviceId())) && reg.getName().equals(ctx.getName())) {
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
    public void unpublishVariables(DeviceContext ctx) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(ctx.getPluginId());
            if (regs != null) {
                for (Iterator<VariableRegistration> iterator = regs.iterator(); iterator.hasNext();) {
                    VariableRegistration reg = iterator.next();
                    if (reg.getPluginId().equals(ctx.getPluginId()) && (!ctx.hasDeviceId() || reg.getDeviceId().equals(ctx.getDeviceId()))) {
                        reg.unregister();
                        iterator.remove();
                    }
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

    protected String createFilter(String objectClass, VariableContext ctx) {
        StringBuilder sb = new StringBuilder("(&(objectClass=").append(objectClass).append(")");
        if (ctx.hasPluginId()) {
            sb.append("(pluginId=").append(ctx.getPluginId()).append(")");
        }
        if (ctx.hasDeviceId()) {
            sb.append("(deviceId=").append(ctx.getDeviceId()).append(")");
        }
        if (ctx.hasName()) {
            sb.append("(name=").append(ctx.getName()).append(")");
        }
        sb.append(")");
        return sb.toString();
    }
}
