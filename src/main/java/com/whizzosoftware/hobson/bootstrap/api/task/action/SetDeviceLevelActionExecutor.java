/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.action;

import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.variable.VariableConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SetDeviceLevelActionExecutor extends AbstractVariableUpdateActionExecutor {
    public static final String LEVEL = "level";

    public SetDeviceLevelActionExecutor(EventManager eventManager) {
        super(eventManager);
    }

    static public List<TypedProperty> getProperties() {
        List<TypedProperty> props = new ArrayList<>();
        props.add(new TypedProperty("devices", "Devices", "The devices to send the command to", TypedProperty.Type.DEVICES));
        props.add(new TypedProperty("level", "Level", "The percent level to set (0-100)", TypedProperty.Type.NUMBER));
        return props;
    }

    @Override
    protected String getVariableName() {
        return VariableConstants.LEVEL;
    }

    @Override
    protected Object getVariableValue(Map<String, Object> propertyValues) {
        return propertyValues.get(LEVEL);
    }
}
