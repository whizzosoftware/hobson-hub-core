/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DevicePassport;
import com.whizzosoftware.hobson.api.device.store.DevicePassportStore;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A MapDB implementation of DeviceBootstrapStore.
 *
 * @author Dan Noguerol
 */
public class MapDBDevicePassportStore implements DevicePassportStore {
    private static final Logger logger = LoggerFactory.getLogger(MapDBDevicePassportStore.class);

    private DB db;
    private CollectionPersister persister = new CollectionPersister();

    public MapDBDevicePassportStore(File file) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            db = DBMaker.newFileDB(file)
                .closeOnJvmShutdown()
                .make();

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<DevicePassport> getAllPassports(HubContext hctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<DevicePassport> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db, "deviceBootstraps");
            for (String key : ctx.getKeySet()) {
                DevicePassport db = persister.restoreDevicePassport(ctx, key);
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
    public DevicePassport getPassport(HubContext hctx, String id) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db, "deviceBootstraps");
            return persister.restoreDevicePassport(ctx, id);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public boolean hasPassportForDeviceId(HubContext hctx, String deviceId) {
        return (getPassportForDeviceId(hctx, deviceId) != null);
    }

    @Override
    public DevicePassport getPassportForDeviceId(HubContext hctx, String deviceId) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db, "deviceBootstraps");
            for (String key : ctx.getKeySet()) {
                DevicePassport db = persister.restoreDevicePassport(ctx, key);
                if (db != null && db.getDeviceId().equals(deviceId)) {
                    return db;
                }
            }
            return null;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public DevicePassport savePassport(HubContext hctx, DevicePassport bootstrap) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding device bootstrap: {}", bootstrap.toString());
            persister.saveDevicePassport(new MapDBCollectionPersistenceContext(db, "deviceBootstraps"), bootstrap);
            return bootstrap;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deletePassport(HubContext hctx, String id) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Deleting device bootstrap: {}", id);
            persister.deleteDevicePassport(new MapDBCollectionPersistenceContext(db, "deviceBootstraps"), id);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
