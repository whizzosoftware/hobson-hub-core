/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.*;
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

    static final String GLOBAL_NAME = "$GLOBAL$";

    private VariableStore variableStore;

    public OSGIVariableManager() {
        this(new OSGIVariableStore());
    }

    public OSGIVariableManager(VariableStore variableStore) {
        this.variableStore = variableStore;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public void applyVariableUpdates(HubContext ctx, List<VariableUpdate> updates) {
        HobsonVariable var;
        List<VariableUpdate> appliedUpdates = new ArrayList<>(); // track which updates were applied

        for (VariableUpdate update : updates) {
            if (update.isGlobal()) {
                var = getDeviceVariable(DeviceContext.create(ctx, update.getPluginId(), GLOBAL_NAME), update.getName());
            } else {
                var = getDeviceVariable(DeviceContext.create(ctx, update.getPluginId(), update.getDeviceId()), update.getName());
            }

            if (var != null && (var.getValue() == null || !var.getValue().equals(update.getValue()))) {
                logger.debug("Applying value for {}.{}.{}: {}", update.getPluginId(), update.getDeviceId(), update.getName(), update.getValue());
                Object oldValue = var.getValue();
                Object newValue = update.getValue();
                if (oldValue == null) {
                    update.setInitial(true);
                }
                // TODO: write unit test to verify this logic
                if ((newValue == null && oldValue != null) || (newValue != null && !newValue.equals(oldValue))) {
                    ((MutableHobsonVariable)var).setValue(update.getValue());
                    appliedUpdates.add(update);
                }
            }
        }

        // if any updates were actually applied, post update events for them
        if (appliedUpdates.size() > 0) {
            eventManager.postEvent(ctx, new VariableUpdateNotificationEvent(System.currentTimeMillis(), appliedUpdates));
        }
    }

    @Override
    public Collection<HobsonVariable> getAllVariables(HubContext ctx) {
        return variableStore.getVariables(ctx, null, null);
    }

    @Override
    public Collection<String> getPublishedVariableNames(HubContext ctx) {
        return variableStore.getVariableNames();
    }

    @Override
    public HobsonVariable getDeviceVariable(DeviceContext ctx, String name) {
        return getUniqueVariable(ctx.getHubContext(), ctx.getPluginId(), ctx.getDeviceId(), name);
    }

    @Override
    public HobsonVariableCollection getDeviceVariables(DeviceContext ctx) {
        return new HobsonVariableCollection(variableStore.getVariables(ctx.getHubContext(), ctx.getPluginId(), ctx.getDeviceId()));
    }

    @Override
    public HobsonVariable getGlobalVariable(HubContext ctx, String name) {
        return getUniqueVariable(ctx, null, null, name);
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(HubContext ctx) {
        return variableStore.getVariables(ctx, null, GLOBAL_NAME);
    }

    @Override
    public boolean hasDeviceVariable(DeviceContext ctx, String name) {
        Collection<HobsonVariable> results = variableStore.getVariables(ctx.getHubContext(), ctx.getPluginId(), ctx.getDeviceId(), name);
        return (results != null && results.size() > 0);
    }

    @Override
    public void publishDeviceVariable(DeviceContext ctx, String name, Object value, HobsonVariable.Mask mask) {
        publishDeviceVariable(ctx, name, value, mask, null);
    }

    @Override
    public void publishDeviceVariable(DeviceContext ctx, String name, Object value, HobsonVariable.Mask mask, VariableMediaType mediaType) {
        // make sure the variable name is legal
        if (name == null || name.contains(",") || name.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish variable \"" + name + "\": name is either null or contains an invalid character");
        }

        // make sure variable doesn't already exist
        if (hasDeviceVariable(ctx, name)) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + ctx.getPluginId() + "," + ctx.getDeviceId() + "," + name);
        }

        logger.debug("Publishing device variable {}[{}] with value {}", ctx, name, value);

        variableStore.publishVariable(
            new MutableHobsonVariable(ctx, name, mask, value, mediaType)
        );
    }

    @Override
    public void publishGlobalVariable(PluginContext ctx, String name, Object value, HobsonVariable.Mask mask) {
        publishGlobalVariable(ctx, name, value, mask, null);
    }

    @Override
    public void publishGlobalVariable(PluginContext ctx, String name, Object value, HobsonVariable.Mask mask, VariableMediaType mediaType) {
        publishDeviceVariable(DeviceContext.create(ctx, GLOBAL_NAME), name, value, mask, mediaType);
    }

    @Override
    public Long setDeviceVariable(DeviceContext ctx, String name, Object value) {
        HobsonVariable variable = getDeviceVariable(ctx, name);
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
                updates.add(new VariableUpdate(ctx, name, values.get(name)));
            }
        }
        eventManager.postEvent(ctx.getPluginContext().getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), updates));
        return results;
    }

    @Override
    public Long setGlobalVariable(PluginContext ctx, String name, Object value) {
        Map<String,Long> val = setGlobalVariables(ctx, Collections.singletonMap(name, value));
        return val.get(name);
    }

    @Override
    public Map<String, Long> setGlobalVariables(PluginContext ctx, Map<String, Object> values) {
        Map<String,Long> results = new HashMap<>();
        List<VariableUpdate> updates = new ArrayList<>();
        for (String name : values.keySet()) {
            HobsonVariable variable = getGlobalVariable(ctx.getHubContext(), name);
            if (variable != null) {
                results.put(name, variable.getLastUpdate());
            }
        }
        eventManager.postEvent(ctx.getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), updates));
        return results;
    }

    @Override
    public void unpublishAllPluginVariables(PluginContext ctx) {
        variableStore.unpublishVariables(ctx, null);
    }

    @Override
    public void unpublishAllDeviceVariables(DeviceContext ctx) {
        variableStore.unpublishVariables(ctx.getPluginContext(), ctx.getDeviceId());
    }

    @Override
    public void unpublishDeviceVariable(DeviceContext ctx, String name) {
        variableStore.unpublishVariable(ctx.getPluginContext(), ctx.getDeviceId(), name);
    }

    @Override
    public void unpublishGlobalVariable(PluginContext ctx, String name) {
        variableStore.unpublishVariable(ctx, null, name);
    }

    protected HobsonVariable getUniqueVariable(HubContext ctx, String pluginId, String deviceId, String name) {
        Collection<HobsonVariable> results = variableStore.getVariables(ctx, pluginId, deviceId, name);
        if (results != null && results.size() > 0) {
            if (results.size() == 1) {
                return results.iterator().next();
            } else {
                throw new HobsonRuntimeException("Found multiple variables for " + ctx + "[" + name + "]");
            }
        } else {
            throw new VariableNotFoundException(ctx, pluginId, deviceId, name);
        }
    }
}