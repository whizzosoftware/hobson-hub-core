/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.presence.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.KeyUtil;
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
import java.util.Map;

public class MapDBPresenceStore implements PresenceStore {
    private static final Logger logger = LoggerFactory.getLogger(MapDBPresenceStore.class);

    private static final String PRESENCE_ENTITIES_KEY = "presenceEntities";
    private static final String PRESENCE_LOCATIONS_KEY = "presenceLocations";

    private DB db;
    private CollectionPersister persister = new CollectionPersister();

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
            MapDBCollectionPersistenceContext pctx = new MapDBCollectionPersistenceContext(db, PRESENCE_ENTITIES_KEY);
            String keyPrefix = KeyUtil.createPresenceEntitiesKey(HubContext.createLocal());
            for (String key : pctx.getKeySet()) {
                if (key.startsWith(keyPrefix)) {
                    Map<String,Object> peMap = pctx.getMap(key);
                    results.add(persister.restorePresenceEntity(pctx, PresenceEntityContext.create((String)peMap.get("context"))));
                }
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

            return persister.restorePresenceEntity(new MapDBCollectionPersistenceContext(db, PRESENCE_ENTITIES_KEY), ctx);

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
            persister.savePresenceEntity(new MapDBCollectionPersistenceContext(db, PRESENCE_ENTITIES_KEY), pe);

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
            persister.deletePresenceEntity(new MapDBCollectionPersistenceContext(db, PRESENCE_ENTITIES_KEY), ctx);
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
            MapDBCollectionPersistenceContext pctx = new MapDBCollectionPersistenceContext(db, PRESENCE_LOCATIONS_KEY);
            String keyPrefix = KeyUtil.createPresenceLocationsKey(HubContext.createLocal());
            for (String key : pctx.getKeySet()) {
                if (key.startsWith(keyPrefix)) {
                    Map<String,Object> peMap = pctx.getMap(key);
                    results.add(persister.restorePresenceLocation(pctx, PresenceLocationContext.create((String) peMap.get("context"))));
                }
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

            return persister.restorePresenceLocation(new MapDBCollectionPersistenceContext(db, PRESENCE_LOCATIONS_KEY), ctx);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void savePresenceLocation(PresenceLocation pel) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding presence location: {}", pel.getContext().toString());
            persister.savePresenceLocation(new MapDBCollectionPersistenceContext(db, PRESENCE_LOCATIONS_KEY), pel);

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
            persister.deletePresenceLocation(new MapDBCollectionPersistenceContext(db, PRESENCE_LOCATIONS_KEY), ctx);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
