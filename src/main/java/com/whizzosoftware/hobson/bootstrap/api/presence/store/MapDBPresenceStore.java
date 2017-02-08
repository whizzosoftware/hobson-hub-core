/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.presence.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.presence.PresenceEntity;
import com.whizzosoftware.hobson.api.presence.PresenceEntityContext;
import com.whizzosoftware.hobson.api.presence.PresenceLocation;
import com.whizzosoftware.hobson.api.presence.PresenceLocationContext;
import com.whizzosoftware.hobson.api.presence.store.PresenceStore;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapDBPresenceStore implements PresenceStore {
    private static final Logger logger = LoggerFactory.getLogger(MapDBPresenceStore.class);

    final private DB db;
    private ContextPathIdProvider idProvider = new ContextPathIdProvider();
    private CollectionPersister persister = new CollectionPersister(new ContextPathIdProvider());

    public MapDBPresenceStore(File file) {
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
    public Collection<PresenceEntity> getAllPresenceEntities(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<PresenceEntity> results = new ArrayList<>();
            MapDBCollectionPersistenceContext pctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : pctx.getSet(idProvider.createPresenceEntitiesId(ctx).getId())) {
                PresenceEntityContext pectx = PresenceEntityContext.create(ctx, (String)o);
                results.add(persister.restorePresenceEntity(pctx, pectx));
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PresenceEntity getPresenceEntity(PresenceEntityContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            return persister.restorePresenceEntity(new MapDBCollectionPersistenceContext(db), ctx);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void savePresenceEntity(PresenceEntity pe) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding presence entity: {}", pe.getContext().toString());
            synchronized (db) {
                persister.savePresenceEntity(new MapDBCollectionPersistenceContext(db), pe, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deletePresenceEntity(PresenceEntityContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Deleting presence entity: {}", ctx.toString());
            synchronized (db) {
                persister.deletePresenceEntity(new MapDBCollectionPersistenceContext(db), ctx);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<PresenceLocation> getAllPresenceLocations(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<PresenceLocation> results = new ArrayList<>();
            MapDBCollectionPersistenceContext pctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : pctx.getSet(idProvider.createPresenceLocationsId(ctx).getId())) {
                PresenceLocationContext plctx = PresenceLocationContext.create(ctx, (String)o);
                results.add(persister.restorePresenceLocation(pctx, plctx));
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PresenceLocation getPresenceLocation(PresenceLocationContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            return persister.restorePresenceLocation(new MapDBCollectionPersistenceContext(db), ctx);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void performHousekeeping() {
        synchronized (db) {
            db.commit();
            db.compact();
        }
    }

    @Override
    public void savePresenceLocation(PresenceLocation pel) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding presence location: {}", pel.getContext().toString());
            synchronized (db) {
                persister.savePresenceLocation(new MapDBCollectionPersistenceContext(db), pel, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deletePresenceLocation(PresenceLocationContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Deleting presence location: {}", ctx.toString());
            synchronized (db) {
                persister.deletePresenceLocation(new MapDBCollectionPersistenceContext(db), ctx);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
