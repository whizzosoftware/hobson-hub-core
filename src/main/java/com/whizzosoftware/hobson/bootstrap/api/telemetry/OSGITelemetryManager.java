/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.telemetry;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.HobsonDevice;
import com.whizzosoftware.hobson.api.telemetry.TelemetryInterval;
import com.whizzosoftware.hobson.api.telemetry.TelemetryManager;
import com.whizzosoftware.hobson.api.telemetry.TemporalValue;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.rrd4j.ConsolFun.AVERAGE;

/**
 * An OSGI implementation of a TelemetryManager.
 *
 * @author Dan Noguerol
 */
public class OSGITelemetryManager implements TelemetryManager {
    private static final String TELEMETRY_DELIMITER = ":";

    volatile DeviceManager deviceManager;

    private final Map<String,Object> telemetryMutexes = new HashMap<>();

    @Override
    public void enableDeviceTelemetry(DeviceContext ctx, boolean enabled) {

    }

    @Override
    public Map<String, Collection<TemporalValue>> getDeviceTelemetry(DeviceContext ctx, long endTime, TelemetryInterval interval) {
        Map<String,Collection<TemporalValue>> results = new HashMap<>();
        HobsonDevice device = deviceManager.getDevice(ctx);
        String[] variables = device.getRuntime().getTelemetryVariableNames();
        if (variables != null) {
            for (String varName : variables) {
                results.put(varName, getDeviceVariableTelemetry(ctx, varName, endTime, interval));
            }
        }
        return results;
    }

    @Override
    public void writeDeviceTelemetry(DeviceContext ctx, Map<String, TemporalValue> values) {

    }

    @Override
    public Collection<TemporalValue> getDeviceVariableTelemetry(DeviceContext ctx, String name, long endTime, TelemetryInterval interval) {
        try {
            List<TemporalValue> results = new ArrayList<>();
            File file = getTelemetryFile(ctx, name);
            if (file.exists()) {
                Object mutex = getTelemetryMutex(ctx, name);
                synchronized (mutex) {
                    RrdDb db = new RrdDb(file.getAbsolutePath(), true);
                    try {
                        FetchRequest fetch = db.createFetchRequest(ConsolFun.AVERAGE, calculateStartTime(endTime, interval), endTime);
                        FetchData data = fetch.fetchData();

                        long[] timestamps = data.getTimestamps();
                        double[] values = data.getValues(0);

                        if (timestamps.length == values.length) {
                            for (int i = 0; i < timestamps.length; i++) {
                                results.add(new TemporalValue(timestamps[i], values[i]));
                            }
                        } else {
                            throw new HobsonRuntimeException("Timestamp and value count is different; telemetry file may be corrupted?");
                        }
                    } finally {
                        db.close();
                    }
                }
            }
            return results;
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error retrieving device telemetry", e);
        }
    }

    @Override
    public void writeDeviceVariableTelemetry(DeviceContext ctx, String name, Object value, long time) {
        try {
            Object mutex = getTelemetryMutex(ctx, name);
            synchronized (mutex) {
                File file = getTelemetryFile(ctx, name);
                RrdDb db = new RrdDb(file.getAbsolutePath(), false);
                Sample sample = db.createSample();
                sample.setAndUpdate(Long.toString(time) + ":" + value);
                db.close();
            }
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error writing to telemetry file", e);
        }
    }

    private Object getTelemetryMutex(DeviceContext ctx, String name) {
        synchronized (telemetryMutexes) {
            String key = createDeviceVariableString(ctx.getPluginId(), ctx.getDeviceId(), name);
            Object mutex = telemetryMutexes.get(key);
            if (mutex == null) {
                mutex = new Object();
                telemetryMutexes.put(key, mutex);
            }
            return mutex;
        }
    }

    private File getTelemetryFile(DeviceContext ctx, String name) throws IOException {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        File file = context.getDataFile(createDeviceVariableString(ctx.getPluginId(), ctx.getDeviceId(), name) + ".rrd");
        if (!file.exists()) {
            RrdDef rrdDef = new RrdDef(file.getAbsolutePath(), 300);
            rrdDef.addDatasource(name, DsType.GAUGE, 300, Double.NaN, Double.NaN);
            rrdDef.addArchive(AVERAGE, 0.5, 1, 2016);
            RrdDb db = new RrdDb(rrdDef);
            db.close();
        }
        return file;
    }

    private String createDeviceVariableString(String pluginId, String deviceId, String name) {
        return pluginId + TELEMETRY_DELIMITER + deviceId + TELEMETRY_DELIMITER + name;
    }

    private long calculateStartTime(long endTime, TelemetryInterval interval) {
        switch (interval) {
            case DAYS_7:
                return endTime - (7 * 24 * 60 * 60L);
            case DAYS_30:
                return endTime - (30 * 24 * 60 * 60L);
            default:
                return endTime - (24 * 60 * 60L);
        }
    }
}
