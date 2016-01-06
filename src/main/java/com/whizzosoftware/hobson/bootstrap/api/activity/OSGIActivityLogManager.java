/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.activity;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.activity.ActivityLogEntry;
import com.whizzosoftware.hobson.api.activity.ActivityLogManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.VariableChange;
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
public class OSGIActivityLogManager implements ActivityLogManager, EventListener {
    private final Logger activityLog = LoggerFactory.getLogger("ACTIVITY");

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile DeviceManager deviceManager;
    private volatile TaskManager taskManager;

    private File activityFile = new File("logs/activity.log");

    public void start() {
        // TODO: if this remains file-based, a file size throttling mechanism is needed
        eventManager.addListener(HubContext.createLocal(), this, new String[]{EventTopics.STATE_TOPIC});
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

    @Override
    public void onHobsonEvent(HobsonEvent event) {
        // TODO: make this more efficient
        if (event instanceof TaskExecutionEvent) {
            appendEvent("Task " + taskManager.getTask(((TaskExecutionEvent)event).getContext()).getName() + " was executed");
        } else if (event instanceof VariableUpdateNotificationEvent) {
            VariableUpdateNotificationEvent vune = (VariableUpdateNotificationEvent)event;
            for (VariableChange change : vune.getUpdates()) {
                if (!change.isInitial() && change.hasNewValue() && change.isChanged() && change.getDeviceContext().hasDeviceId()) {
                    HobsonDevice device = deviceManager.getDevice(change.getDeviceContext());
                    String s = createVariableChangeString(device.getName(), change.getName(), change.getNewValue());
                    if (s != null) {
                        appendEvent(s);
                    }
                }
            }
        }
    }

    protected void appendEvent(String s) {
        activityLog.info(s);
    }

    protected String createVariableChangeString(String deviceName, String varName, Object varValue) {
        switch (varName) {
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
