/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.action.AbstractHobsonAction;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaData;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaDataEnumValue;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaDataEnumValueParam;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * An action that can control a device.
 *
 * @author Dan Noguerol
 */
public class SendCommandToDeviceAction extends AbstractHobsonAction {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String TURN_OFF = "turnOff";
    private static final String TURN_ON = "turnOn";
    private static final String SET_LEVEL = "setLevel";

    private final Map<String,ActionMetaDataEnumValue> metaEnumValues = new HashMap<>();

    public SendCommandToDeviceAction(String pluginId) {
        super(pluginId, "sendDeviceCommand", "Send Command to Device");

        buildMetaEnums();

        addMetaData(new ActionMetaData("pluginId", "Plugin ID", "The plugin that owns the device", ActionMetaData.Type.STRING));
        addMetaData(new ActionMetaData("deviceId", "Device ID", "The device to send the command to", ActionMetaData.Type.STRING));

        ActionMetaData control = new ActionMetaData("commandId", "Command", "The command to send to the device", ActionMetaData.Type.ENUMERATION);
        for (ActionMetaDataEnumValue val : metaEnumValues.values()) {
            control.addEnumValue(val);
        }
        addMetaData(control);
    }

    @Override
    public void execute(Map<String, Object> properties) {
        try {
            VariableUpdate vup = createVariableUpdate(properties);
            getVariableManager().setDeviceVariable(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, vup.getPluginId(), vup.getDeviceId(), vup.getName(), vup.getValue());
        } catch (Exception e) {
            logger.error("Error sending command to device", e);
        }
    }

    protected void buildMetaEnums() {
        metaEnumValues.put(TURN_OFF, new ActionMetaDataEnumValue(TURN_OFF, "Turn off", null, "on"));
        metaEnumValues.put(TURN_ON, new ActionMetaDataEnumValue(TURN_ON, "Turn on", null, "on"));
        metaEnumValues.put(SET_LEVEL, new ActionMetaDataEnumValue(SET_LEVEL, "Set level", new ActionMetaDataEnumValueParam("Level", "A percentage (0-100) for the device's level", ActionMetaData.Type.NUMBER), "level"));
    }

    protected VariableUpdate createVariableUpdate(Map<String, Object> properties) {
        String commandId = (String)properties.get("commandId");
        return new VariableUpdate(
                (String)properties.get("pluginId"),
                (String)properties.get("deviceId"),
                getVariableNameForCommandId(commandId),
                getVariableValueForCommandId(commandId, properties.get("param"))
        );
    }

    protected String getVariableNameForCommandId(String commandId) {
        ActionMetaDataEnumValue amev = metaEnumValues.get(commandId);
        if (amev != null) {
            return amev.getRequiredDeviceVariable();
        } else {
            throw new IllegalArgumentException("Invalid commandId: " + commandId);
        }
    }

    protected Object getVariableValueForCommandId(String commandId, Object param) {
        if (TURN_ON.equals(commandId)) {
            return true;
        } else if (TURN_OFF.equals(commandId)) {
            return false;
        } else if (SET_LEVEL.equals(commandId)) {
            return param;
        } else {
            throw new IllegalArgumentException("Invalid commandId: " + commandId);
        }
    }
}
