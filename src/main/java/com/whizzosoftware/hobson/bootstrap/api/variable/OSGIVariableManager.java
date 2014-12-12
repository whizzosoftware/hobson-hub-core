/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.util.VariableChangeIdHelper;
import com.whizzosoftware.hobson.api.variable.*;
import com.whizzosoftware.hobson.api.variable.telemetry.TelemetryInterval;
import com.whizzosoftware.hobson.api.variable.telemetry.TemporalValue;
import org.osgi.framework.*;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import static org.rrd4j.ConsolFun.AVERAGE;

/**
 * A VariableManager implementation that publishes variables as OSGi services and uses OSGi events to
 * change them.
 *
 * @author Dan Noguerol
 */
public class OSGIVariableManager implements VariableManager {
    private static final Logger logger = LoggerFactory.getLogger(OSGIVariableManager.class);

    private volatile EventManager eventManager;
    private volatile ConfigurationAdmin configAdmin;

    private static final String GLOBAL_NAME = "$GLOBAL$";
    private static final String TELEMETRY_PID = "com.whizzosoftware.hobson.hub.telemetry";
    private static final String TELEMETRY_DELIMITER = ":";

    private final Map<String,List<VariableRegistration>> variableRegistrations = new HashMap<>();
    private final Map<String,Object> telemetryMutexes = new HashMap<>();

    @Override
    public void publishGlobalVariable(String userId, String hubId, String pluginId, HobsonVariable var) {
        publishDeviceVariable(userId, hubId, pluginId, GLOBAL_NAME, var);
    }

    @Override
    public Collection<HobsonVariable> getGlobalVariables(String userId, String hubId) {
        List<HobsonVariable> results = new ArrayList<>();
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + "))");
            if (references != null && references.length > 0) {
                for (ServiceReference ref : references) {
                    results.add((HobsonVariable)bundleContext.getService(ref));
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving global variables", e);
        }
        return results;
    }

    @Override
    public HobsonVariable getGlobalVariable(String userId, String hubId, String name) {
        BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        try {
            ServiceReference[] references = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(deviceId=" + GLOBAL_NAME + ")(name=" + name + "))");
            if (references != null && references.length > 0) {
                if (references.length > 1) {
                    throw new HobsonRuntimeException("Found multiple global variables for " + name + "]");
                } else {
                    return (HobsonVariable)bundleContext.getService(references[0]);
                }
            } else {
                throw new GlobalVariableNotFoundException(name);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving global variables", e);
        }
    }

    @Override
    public void publishDeviceVariable(String userId, String hubId, String pluginId, String deviceId, HobsonVariable var) {
        // make sure the variable name is legal
        if (var.getName() == null || var.getName().contains(",") || var.getName().contains(":")) {
            throw new HobsonRuntimeException("Unable to publish variable \"" + var.getName() + "\": name is either null or contains an invalid character");
        }

        // make sure variable doesn't already exist
        if (hasDeviceVariable(userId, hubId, pluginId, deviceId, var.getName())) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate variable: " + pluginId + "," + deviceId + "," + var.getName());
        }

        // publish the variable
        Properties props = new Properties();
        props.setProperty("pluginId", pluginId);
        props.setProperty("deviceId", deviceId);
        props.setProperty("name", var.getName());
        addVariableRegistration(pluginId, deviceId, var.getName(), getBundleContext().registerService(
            HobsonVariable.class.getName(),
            var,
            props
        ));
    }

    @Override
    public void unpublishGlobalVariable(String userId, String hubId, String pluginId, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getName().equals(name)) {
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
    public void unpublishAllPluginVariables(String userId, String hubId, String pluginId) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                for (VariableRegistration vr : regs) {
                    vr.unregister();
                }
                variableRegistrations.remove(pluginId);
            }
        }
    }

    @Override
    public void unpublishDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getDeviceId().equals(deviceId) && reg.getName().equals(name)) {
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
    public void unpublishAllDeviceVariables(String userId, String hubId, String pluginId, String deviceId) {
        synchronized (variableRegistrations) {
            List<VariableRegistration> regs = variableRegistrations.get(pluginId);
            if (regs != null) {
                VariableRegistration vr = null;
                for (VariableRegistration reg : regs) {
                    if (reg.getPluginId().equals(pluginId) && reg.getDeviceId().equals(deviceId)) {
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
    public Collection<HobsonVariable> getDeviceVariables(String userId, String hubId, String pluginId, String deviceId) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(HobsonVariable.class.getName(), "(&(objectClass=" + HobsonVariable.class.getName() + ")(pluginId=" + pluginId + ")(deviceId=" + deviceId + "))");
                if (refs != null) {
                    List<HobsonVariable> results = new ArrayList<>();
                    for (ServiceReference ref : refs) {
                        results.add((HobsonVariable)bundleContext.getService(ref));
                    }
                    return results;
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variables for device ID \"" + deviceId + "\"", e);
            }
        }
        return null;
    }

    @Override
    public Collection<String> getDeviceVariableChangeIds(String userId, String hubId, String pluginId, String deviceId) {
        List<String> eventIds = new ArrayList<>();

        Collection<HobsonVariable> deviceVars = getDeviceVariables(userId, hubId, pluginId, deviceId);
        for (HobsonVariable v : deviceVars) {
            VariableChangeIdHelper.populateChangeIdsForVariableName(v.getName(), eventIds);
        }

        return eventIds;
    }

    @Override
    public HobsonVariable getDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(null, "(&(objectClass=" +
                    HobsonVariable.class.getName() +
                    ")(pluginId=" +
                    pluginId +
                    ")(deviceId=" +
                    deviceId +
                    ")(name=" +
                    name +
                    "))"
                );
                if (refs != null && refs.length == 1) {
                    return (HobsonVariable) bundleContext.getService(refs[0]);
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + pluginId + "." + deviceId + "[" + name + "]");
                } else {
                    throw new DeviceVariableNotFoundException(pluginId, deviceId, name);
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + pluginId + "." + deviceId + "[" + name + "]", e);
            }
        }
        return null;
    }

    @Override
    public boolean hasDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name) {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext != null) {
            try {
                ServiceReference[] refs = bundleContext.getServiceReferences(null, "(&(objectClass=" +
                    HobsonVariable.class.getName() +
                    ")(pluginId=" +
                    pluginId +
                    ")(deviceId=" +
                    deviceId +
                    ")(name=" +
                    name +
                    "))"
                );
                if (refs != null && refs.length == 1) {
                    return true;
                } else if (refs != null && refs.length > 1) {
                    throw new HobsonRuntimeException("Found multiple variables for " + pluginId + "." + deviceId + "[" + name + "]");
                }
            } catch (InvalidSyntaxException e) {
                throw new HobsonRuntimeException("Error looking up variable " + pluginId + "." + deviceId + "[" + name + "]", e);
            }
        }
        return false;
    }

    @Override
    public Long setDeviceVariable(String userId, String hubId, String pluginId, String deviceId, String name, Object value) {
        HobsonVariable variable = getDeviceVariable(userId, hubId, pluginId, deviceId, name);
        Long lastUpdate = variable.getLastUpdate();
        eventManager.postEvent(userId, hubId, new VariableUpdateRequestEvent(pluginId, deviceId, name, value));
        return lastUpdate;
    }

    @Override
    public void enableDeviceVariableTelemetry(String userId, String hubId, String pluginId, String deviceId, String name, boolean enabled) {
        try {
            Configuration c = configAdmin.getConfiguration(TELEMETRY_PID);
            Dictionary d = c.getProperties();
            if (d == null) {
                d = new Hashtable();
            }
            d.put(createDeviceVariableString(pluginId, deviceId, name), enabled);
            c.update(d);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error updating device variable telemetry configuration", e);
        }
    }

    @Override
    public Collection<DeviceVariableRef> getTelemetryEnabledDeviceVariables(String userId, String hubId) {
        try {
            List<DeviceVariableRef> results = new ArrayList<>();
            Configuration c = configAdmin.getConfiguration(TELEMETRY_PID);
            Dictionary d = c.getProperties();
            if (d != null) {
                Enumeration e = d.keys();
                while (e.hasMoreElements()) {
                    String name = (String)e.nextElement();
                    Boolean enabled = false;
                    Object o = d.get(name);
                    if (o instanceof Boolean) {
                        enabled = (Boolean)o;
                    } else if (o instanceof String) {
                        enabled = Boolean.parseBoolean((String)o);
                    }
                    if (enabled) {
                        results.add(createDeviceVariableRef(name));
                    }
                }
            }
            return results;
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error retrieving device variable telemetry configuration", e);
        }
    }

    @Override
    public void writeDeviceVariableTelemetry(String userId, String hubId, String pluginId, String deviceId, String name, Object value, long time) {
        try {
            Object mutex = getTelemetryMutex(pluginId, deviceId, name);
            synchronized (mutex) {
                File file = getTelemetryFile(pluginId, deviceId, name);
                RrdDb db = new RrdDb(file.getAbsolutePath(), false);
                Sample sample = db.createSample();
                sample.setAndUpdate(Long.toString(time) + ":" + value);
                db.close();
            }
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error writing to telemetry file", e);
        }
    }

    @Override
    public Collection<TemporalValue> getDeviceVariableTelemetry(String userId, String hubId, String pluginId, String deviceId, String name, long endTime, TelemetryInterval interval) {
        try {
            List<TemporalValue> results = new ArrayList<>();
            File file = getTelemetryFile(pluginId, deviceId, name);
            if (file.exists()) {
                Object mutex = getTelemetryMutex(pluginId, deviceId, name);
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
    public void fireVariableUpdateNotification(final String userId, final String hubId, HobsonPlugin plugin, final VariableUpdate update) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    setVariable(userId, hubId, update, true);
                } catch (DeviceVariableNotFoundException e) {
                    logger.error("Attempt to update a variable that has not been published: {}", update);
                }
            }
        });
    }

    @Override
    public void fireVariableUpdateNotifications(final String userId, final String hubId, HobsonPlugin plugin, final List<VariableUpdate> updates) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    for (VariableUpdate update : updates) {
                        setVariable(userId, hubId, update, false);
                    }
                    eventManager.postEvent(userId, hubId, new VariableUpdateNotificationEvent(updates));
                } catch (DeviceVariableNotFoundException e) {
                    logger.error("Attempt to update a variable that has not been published: {}", updates);
                }
            }
        });
    }

    protected void setVariable(String userId, String hubId, VariableUpdate update, boolean generateEvent) {
        HobsonVariable var;

        // if the device ID is null, it's a global variable
        if (update.getDeviceId() == null) {
            var = getDeviceVariable(userId, hubId, update.getPluginId(), GLOBAL_NAME, update.getName());
        } else {
            var = getDeviceVariable(userId, hubId, update.getPluginId(), update.getDeviceId(), update.getName());
        }

        if (var != null) {
            ((HobsonVariableImpl)var).setValue(update.getValue());
        }
        if (generateEvent) {
            eventManager.postEvent(userId, hubId, new VariableUpdateNotificationEvent(update));
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

    private DeviceVariableRef createDeviceVariableRef(String s) {
        StringTokenizer tok = new StringTokenizer(s, TELEMETRY_DELIMITER);
        return new DeviceVariableRef(tok.nextToken(), tok.nextToken(), tok.nextToken());
    }

    private String createDeviceVariableString(String pluginId, String deviceId, String name) {
        return pluginId + TELEMETRY_DELIMITER + deviceId + TELEMETRY_DELIMITER + name;
    }

    private Object getTelemetryMutex(String pluginId, String deviceId, String name) {
        synchronized (telemetryMutexes) {
            String key = createDeviceVariableString(pluginId, deviceId, name);
            Object mutex = telemetryMutexes.get(key);
            if (mutex == null) {
                mutex = new Object();
                telemetryMutexes.put(key, mutex);
            }
            return mutex;
        }
    }

    private File getTelemetryFile(String pluginId, String deviceId, String name) throws IOException {
        BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
        File file = context.getDataFile(createDeviceVariableString(pluginId, deviceId, name) + ".rrd");
        if (!file.exists()) {
            RrdDef rrdDef = new RrdDef(file.getAbsolutePath(), 300);
            rrdDef.addDatasource(name, DsType.GAUGE, 300, Double.NaN, Double.NaN);
            rrdDef.addArchive(AVERAGE, 0.5, 1, 2016);
            RrdDb db = new RrdDb(rrdDef);
            db.close();
        }
        return file;
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

    private class VariableRegistration {
        private String pluginId;
        private String deviceId;
        private String name;
        private ServiceRegistration registration;

        public VariableRegistration(String pluginId, String deviceId, String name, ServiceRegistration registration) {
            this.pluginId = pluginId;
            this.deviceId = deviceId;
            this.name = name;
            this.registration = registration;
        }

        public String getPluginId() {
            return pluginId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getName() {
            return name;
        }

        public void unregister() {
            registration.unregister();
        }
    }
}