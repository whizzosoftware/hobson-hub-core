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
    public void fireVariableUpdateNotifications(HubContext ctx, List<VariableUpdate> updates) {
        HobsonVariable var;
        List<VariableChange> appliedUpdates = null;

        for (VariableUpdate update : updates) {
            VariableContext dvctx;
            if (update.isGlobal()) {
                dvctx = VariableContext.createGlobal(ctx, update.getPluginId(), update.getName());
            } else {
                dvctx = VariableContext.create(ctx, update.getPluginId(), update.getDeviceId(), update.getName());
            }
            var = getVariable(dvctx);

            if (var != null) {
                logger.debug("Applying value for {}.{}.{}: {}", update.getPluginId(), update.getDeviceId(), update.getName(), update.getValue());
                Object newValue = update.getValue();

                // record the update that was applied for notification purposes
                if (appliedUpdates == null) {
                    appliedUpdates = new ArrayList<>();
                }
                appliedUpdates.add(new VariableChange(dvctx, var != null ? var.getValue() : null, update.getValue()));

                // set the new value
                ((MutableHobsonVariable)var).setValue(newValue);
            } else {
                throw new VariableNotFoundException("Attempted to update an unknown variable: " + dvctx);
            }
        }

        // if any updates were actually applied, post update events for them
        if (appliedUpdates != null && appliedUpdates.size() > 0) {
            eventManager.postEvent(ctx, new VariableUpdateNotificationEvent(System.currentTimeMillis(), appliedUpdates));
        }
    }

    @Override
    public Collection<HobsonVariable> getAllVariables(HubContext ctx) {
        return variableStore.getAllVariables(ctx);
    }

    @Override
    public Collection<String> getPublishedVariableNames(HubContext ctx) {
        return variableStore.getVariableNames();
    }

    @Override
    public HobsonVariable getVariable(VariableContext ctx) {
        return variableStore.getVariable(ctx);
    }

    @Override
    public Collection<HobsonVariable> getDeviceVariables(DeviceContext ctx) {
        return variableStore.getDeviceVariables(ctx);
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(HubContext ctx) {
        return variableStore.getAllGlobalVariables(ctx);
    }

    @Override
    public boolean hasVariable(VariableContext ctx) {
        return variableStore.hasVariable(ctx);
    }

    @Override
    public void publishVariable(VariableContext ctx, Object value, HobsonVariable.Mask mask) {
        publishVariable(ctx, value, mask, null);
    }

    @Override
    public void publishVariable(VariableContext ctx, Object value, HobsonVariable.Mask mask, VariableMediaType mediaType) {
        // make sure the variable name is legal
        String name = ctx.getName();
        if (name == null || name.contains(",") || name.contains(":")) {
            throw new HobsonRuntimeException("Unable to publish variable \"" + name + "\": name is either null or contains an invalid character");
        }

        // make sure variable doesn't already exist
        if (hasVariable(ctx)) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + ctx.getPluginId() + "," + ctx.getDeviceId() + "," + name);
        }

        logger.debug("Publishing device variable {}[{}] with value {}", ctx, name, value);

        variableStore.publishVariable(
            new MutableHobsonVariable(ctx, mask, value, mediaType)
        );
    }

    @Override
    public Long setVariable(VariableContext ctx, Object value) {
        HobsonVariable variable = getVariable(ctx);
        if (variable != null) {
            Long lastUpdate = variable.getLastUpdate();
            logger.debug("Attempting to set variable {}.{}.{} to value {}", ctx.getPluginId(), ctx.getDeviceId(), ctx.getName(), value);
            eventManager.postEvent(ctx.getPluginContext().getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), new VariableUpdate(ctx, value)));
            return lastUpdate;
        } else {
            throw new VariableNotFoundException("Attempted to set an unknown variable: " + ctx);
        }
    }

    @Override
    public Map<String, Long> setDeviceVariables(DeviceContext ctx, Map<String, Object> values) {
        Map<String,Long> results = new HashMap<>();
        List<VariableUpdate> updates = new ArrayList<>();
        for (String name : values.keySet()) {
            HobsonVariable variable = getVariable(VariableContext.create(ctx, name));
            if (variable != null) {
                results.put(name, variable.getLastUpdate());
                updates.add(new VariableUpdate(VariableContext.create(ctx, name), values.get(name)));
            }
        }
        eventManager.postEvent(ctx.getPluginContext().getHubContext(), new VariableUpdateRequestEvent(System.currentTimeMillis(), updates));
        return results;
    }

    @Override
    public void unpublishAllVariables(PluginContext ctx) {
        variableStore.unpublishVariables(DeviceContext.create(ctx, null));
    }

    @Override
    public void unpublishAllVariables(DeviceContext ctx) {
        variableStore.unpublishVariables(ctx);
    }

    @Override
    public void unpublishVariable(VariableContext ctx) {
        variableStore.unpublishVariable(ctx);
    }
}