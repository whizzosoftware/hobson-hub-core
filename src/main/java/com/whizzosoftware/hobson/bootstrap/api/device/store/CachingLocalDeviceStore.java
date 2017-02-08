/*
 *******************************************************************************
 * Copyright (c) 2017 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A DeviceStore implementation that wrappers an existing device store to provide weakly-references caching
 * of HobsonDeviceDescriptor objects. It makes the optimization assumption that it will only be used as the
 * wrapper for a local hub.
 *
 * @author Dan Noguerol
 */
public class CachingLocalDeviceStore implements DeviceStore, Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CachingLocalDeviceStore.class);

    private DeviceStore deviceStore;
    private final List<DeviceContext> devices = Collections.synchronizedList(new ArrayList<DeviceContext>());
    private final Map<DeviceContext,SoftReference<HobsonDeviceDescriptor>> deviceMap = Collections.synchronizedMap(new HashMap<DeviceContext,SoftReference<HobsonDeviceDescriptor>>());
    private final BlockingQueue<Runnable> saveQueue = new LinkedBlockingDeque<>();
    private Thread saveThread;

    public CachingLocalDeviceStore(DeviceStore deviceStore) {
        // populate initial cache
        this.deviceStore = deviceStore;

        synchronized (devices) {
            for (HobsonDeviceDescriptor dd : deviceStore.getAllDevices(HubContext.createLocal())) {
                devices.add(dd.getContext());
                deviceMap.put(dd.getContext(), new SoftReference<>(dd));
            }
        }

        saveThread = new Thread(this, "CachingLocalDeviceStore Commit");
        saveThread.start();
    }

    public void run() {
        try {
            while (true) {
                Runnable r = saveQueue.take();
                logger.trace("Processing save queue");
                r.run();
            }
        } catch (InterruptedException ignored) {}
    }

    @Override
    public void start() {
        deviceStore.start();
    }

    @Override
    public void stop() {
        deviceStore.stop();
        saveThread.interrupt();
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(HubContext ctx) {
        if (ctx.isLocal()) {
            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            synchronized (devices) {
                for (DeviceContext dctx : devices) {
                    results.add(getDevice(dctx));
                }
            }
            return results;
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(PluginContext ctx) {
        if (ctx.getHubContext().isLocal()) {
            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            synchronized (devices) {
                for (DeviceContext dctx : devices) {
                    if (dctx.getPluginContext().equals(ctx)) {
                        results.add(getDevice(dctx));
                    }
                }
            }
            return results;
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return devices.contains(ctx);
    }

    @Override
    public void performHousekeeping() {
        deviceStore.performHousekeeping();
    }

    @Override
    public HobsonDeviceDescriptor getDevice(DeviceContext ctx) {
        SoftReference<HobsonDeviceDescriptor> r = deviceMap.get(ctx);
        HobsonDeviceDescriptor dd = (r != null) ? r.get() : null;
        if (dd == null) {
            dd = getDeviceInternal(ctx);
            if (dd != null) {
                deviceMap.put(ctx, new SoftReference<>(dd));
            }
        }
        return dd;
    }

    @Override
    public String getDeviceName(DeviceContext ctx) {
        HobsonDeviceDescriptor dd = getDevice(ctx);
        return (dd != null) ? dd.getName() : null;
    }

    @Override
    public Set<String> getDeviceTags(DeviceContext ctx) {
        HobsonDeviceDescriptor dd = getDevice(ctx);
        return (dd != null) ? dd.getTags() : null;
    }

    @Override
    public void saveDevice(final HobsonDeviceDescriptor device) {
        logger.trace("saveDevice: {}", device.getContext());
        synchronized (devices) {
            if (!devices.contains(device.getContext())) {
                devices.add(device.getContext());
                deviceMap.put(device.getContext(), new SoftReference<>(device));
            }
        }
        saveQueue.add(new Runnable() {
            @Override
            public void run() {
                deviceStore.saveDevice(device);
            }
        });
    }

    @Override
    public void saveDeviceVariable(final DeviceVariableDescriptor dvd) {
        logger.trace("saveDeviceVariable: {}", dvd.getContext());
        HobsonDeviceDescriptor dd = getDevice(dvd.getContext().getDeviceContext());
        if (dd != null) {
            dd.setVariableDescriptor(dvd);
        }
        saveQueue.add(new Runnable() {
                @Override
                public void run() {
                    deviceStore.saveDeviceVariable(dvd);
                }
            });
    }

    @Override
    public void setDeviceName(final DeviceContext dctx, final String name) {
        logger.trace("setDeviceName {}: {}", dctx, name);
        HobsonDeviceDescriptor dd = getDevice(dctx);
        if (dd != null) {
            dd.setName(name);
        }
        saveQueue.add(new Runnable() {
            @Override
            public void run() {
                deviceStore.setDeviceName(dctx, name);
            }
        });
    }

    @Override
    public void setDeviceTags(final DeviceContext dctx, final Set<String> tags) {
        logger.trace("setDeviceTags {}: {}", dctx, tags);
        HobsonDeviceDescriptor dd = getDevice(dctx);
        if (dd != null) {
            dd.setTags(tags);
        }
        saveQueue.add(new Runnable() {
            @Override
            public void run() {
                deviceStore.setDeviceTags(dctx, tags);
            }
        });
    }

    @Override
    public void deleteDevice(final DeviceContext ctx) {
        synchronized (devices) {
            devices.remove(ctx);
            deviceMap.remove(ctx);
        }
        saveQueue.add(new Runnable() {
            @Override
            public void run() {
                deviceStore.deleteDevice(ctx);
            }
        });
    }

    private HobsonDeviceDescriptor getDeviceInternal(DeviceContext dctx) {
        return deviceStore.getDevice(dctx);
    }
}
