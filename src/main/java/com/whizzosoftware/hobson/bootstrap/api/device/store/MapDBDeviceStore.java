/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
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
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableDescriptor;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MapDBDeviceStore implements DeviceStore {
    final private DB db;
    private IdProvider idProvider = new ContextPathIdProvider();
    private CollectionPersister persister = new CollectionPersister(idProvider);
    private CollectionPersistenceContext mctx;

    public MapDBDeviceStore(File file) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            db = DBMaker.newFileDB(file)
                .closeOnJvmShutdown()
                .make();
            mctx = new MapDBCollectionPersistenceContext(db);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(HubContext hctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            for (Object o : mctx.getSet(idProvider.createDevicesId(hctx).getId())) {
                String key = (String)o;
                HobsonDeviceDescriptor db = persister.restoreDevice(mctx, idProvider.createDeviceContext(key));
                if (db != null) {
                    results.add(db);
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(PluginContext pctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            for (Object o : mctx.getSet(idProvider.createPluginDevicesId(pctx).getId())) {
                String key = (String)o;
                HobsonDeviceDescriptor db = persister.restoreDevice(mctx, idProvider.createDeviceContext(key));
                if (db != null) {
                    results.add(db);
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return (getDevice(ctx) != null); // TODO: inefficient
    }

    @Override
    public void performHousekeeping() {
        synchronized (db) {
            db.commit();
            db.compact();
        }
    }

    @Override
    public HobsonDeviceDescriptor getDevice(DeviceContext dctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return persister.restoreDevice(mctx, dctx);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public String getDeviceName(DeviceContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return persister.restoreDeviceName(mctx, ctx);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Set<String> getDeviceTags(DeviceContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return persister.restoreDeviceTags(mctx, ctx);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    synchronized public void saveDevice(HobsonDeviceDescriptor device) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveDevice(mctx, device, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void saveDeviceVariable(DeviceVariableDescriptor dvd) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveDeviceVariableDescription(mctx, dvd, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setDeviceName(DeviceContext ctx, String name) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveDeviceName(mctx, ctx, name, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setDeviceTags(DeviceContext ctx, Set<String> tags) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveDeviceTags(mctx, ctx, tags, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    synchronized public void deleteDevice(DeviceContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.deleteDevice(mctx, ctx);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
