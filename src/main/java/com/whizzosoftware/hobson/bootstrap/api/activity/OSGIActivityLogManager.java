/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.activity;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.activity.ActivityLogEntry;
import com.whizzosoftware.hobson.api.activity.ActivityLogManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.device.DeviceVariableUpdateEvent;
import com.whizzosoftware.hobson.api.event.task.TaskExecutionEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.DeviceVariableUpdate;
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An OSGi implementation of the ActivityLogManager interface. This initial implementation simply writes entries to a
 * flat file.
 *
 * @author Dan Noguerol
 */
public class OSGIActivityLogManager implements ActivityLogManager {
    private final Logger activityLog = LoggerFactory.getLogger("ACTIVITY");

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile DeviceManager deviceManager;
    private volatile TaskManager taskManager;

    private File activityFile = new File("logs/activity.log");

    public void start() {
        // TODO: if this remains file-based, a file size throttling mechanism is needed
        eventManager.addListener(HubContext.createLocal(), this);
    }

    @Override
    public List<ActivityLogEntry> getActivityLog(long eventCount) {
        List<ActivityLogEntry> events = new ArrayList<>();
        try {
            if (activityFile.exists()) {
                try (ReversedLinesFileReader reader = new ReversedLinesFileReader(activityFile)) {
                    String line;
                    while ((line = reader.readLine()) != null && events.size() < eventCount) {
                        JSONObject json = new JSONObject(line);
                        events.add(new ActivityLogEntry(json.getLong("timestamp"), json.getString("name")));
                    }
                }
            }
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to read activity events", e);
        }
        return events;
    }

    @EventHandler
    public void handleTaskExecution(TaskExecutionEvent event) {
        appendEvent("Task " + taskManager.getTask(((TaskExecutionEvent)event).getContext()).getName() + " was executed");
    }

    @EventHandler
    public void handleDeviceVariableUpdate(DeviceVariableUpdateEvent event) {
        DeviceVariableUpdateEvent vune = (DeviceVariableUpdateEvent)event;
        for (DeviceVariableUpdate update : vune.getUpdates()) {
            if (!update.isInitial() && update.hasNewValue() && update.isChanged() && update.getContext().hasDeviceId()) {
                HobsonDeviceDescriptor device = deviceManager.getDevice(update.getContext().getDeviceContext());
                String s = createVariableChangeString(device.getName(), update.getName(), update.getNewValue());
                if (s != null) {
                    appendEvent(s);
                }
            }
        }
    }

    protected void appendEvent(String s) {
        activityLog.info(s);
    }

    protected String createVariableChangeString(String deviceName, String varName, Object varValue) {
        switch (varName) {
            case VariableConstants.ARMED:
                if ((Boolean)varValue) {
                    return deviceName + " was armed";
                } else {
                    return deviceName + " was disarmed";
                }
            case VariableConstants.ON:
                if ((Boolean)varValue) {
                    return deviceName + " was turned on";
                } else {
                    return deviceName + " was turned off";
                }
            case VariableConstants.LEVEL:
                return deviceName + " level was changed";
            case VariableConstants.TARGET_TEMP_F:
            case VariableConstants.TARGET_TEMP_C:
                return deviceName + " target temperature set to " + varValue;
            case VariableConstants.COLOR:
                return deviceName + " color was changed";
            default:
                return null;
        }
    }
}
