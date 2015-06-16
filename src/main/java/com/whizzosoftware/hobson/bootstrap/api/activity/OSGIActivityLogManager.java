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
import com.whizzosoftware.hobson.api.variable.VariableConstants;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;
import org.apache.commons.io.FileUtils;
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
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile EventManager eventManager;
    private volatile DeviceManager deviceManager;

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
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < eventCount) {
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
            TaskExecutionEvent tee = (TaskExecutionEvent)event;
            JSONObject json = new JSONObject();
            json.put("timestamp", tee.getTimestamp());
            json.put("name", "Task " + tee.getName() + " was executed");
            appendEvent(json.toString());
        } else if (event instanceof VariableUpdateNotificationEvent) {
            VariableUpdateNotificationEvent vune = (VariableUpdateNotificationEvent)event;
            for (VariableUpdate update : vune.getUpdates()) {
                HobsonDevice device = deviceManager.getDevice(update.getDeviceContext());
                String s = createVariableChangeString(device.getName(), update.getName(), update.getValue());
                if (s != null) {
                    JSONObject json = new JSONObject();
                    json.put("timestamp", update.getTimestamp());
                    json.put("name", s);
                    appendEvent(json.toString());
                }
            }
        }
    }

    protected void appendEvent(String s) {
        try {
            FileUtils.writeStringToFile(activityFile, s + "\n", true);
        } catch (IOException e) {
            logger.error("Error writing to activity log");
        }
    }

    protected String createVariableChangeString(String deviceName, String varName, Object varValue) {
        switch (varName) {
            case VariableConstants.ON:
                if ((Boolean)varValue) {
                    return deviceName + " was turned on";
                } else {
                    return deviceName + " was turned off";
                }
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
