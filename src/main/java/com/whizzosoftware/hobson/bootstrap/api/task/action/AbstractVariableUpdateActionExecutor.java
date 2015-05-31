/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;

import java.util.*;

abstract public class AbstractVariableUpdateActionExecutor {
    private EventManager eventManager;

    public AbstractVariableUpdateActionExecutor(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void execute(Map<String,Object> propertyValues) {
        eventManager.postEvent(
            HubContext.createLocal(),
            new VariableUpdateRequestEvent(
                System.currentTimeMillis(),
                createVariableUpdates(propertyValues)
            )
        );
    }

    protected List<VariableUpdate> createVariableUpdates(Map<String, Object> propertyValues) {
        if (propertyValues.containsKey("device")) {
            DeviceContext ctx = (DeviceContext)propertyValues.get("device");
            return Collections.singletonList(new VariableUpdate(ctx, getVariableName(), getVariableValue(propertyValues)));
        } else if (propertyValues.containsKey("devices")) {
            List<VariableUpdate> results = new ArrayList<>();
            List<DeviceContext> contexts = (List<DeviceContext>)propertyValues.get("devices");
            for (DeviceContext ctx : contexts) {
                results.add(new VariableUpdate(ctx, getVariableName(), getVariableValue(propertyValues)));
            }
            return results;
        } else {
            throw new HobsonRuntimeException("No devices found to turn on in task action data");
        }
    }

    abstract protected String getVariableName();
    abstract protected Object getVariableValue(Map<String,Object> propertyValues);
}
