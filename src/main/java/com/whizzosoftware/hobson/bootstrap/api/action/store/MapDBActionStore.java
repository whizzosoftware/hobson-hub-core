/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.action.store;

import com.whizzosoftware.hobson.api.action.store.ActionStore;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * An implementation of ActionStore that uses MapDB for persistent storage.
 *
 * @author Dan Noguerol
 */
public class MapDBActionStore implements ActionStore {
    private static final Logger logger = LoggerFactory.getLogger(MapDBActionStore.class);

    private DB db;
    private IdProvider idProvider = new ContextPathIdProvider();
    private CollectionPersister persister = new CollectionPersister(idProvider);

    public MapDBActionStore(File file) {
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
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<PropertyContainerSet> results = new ArrayList<>();
            MapDBCollectionPersistenceContext mctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : mctx.getSet(idProvider.createActionSetsId(ctx).getId())) {
                String key = (String)o;
                String actionSetId = persister.getActionSetIdFromKey(ctx, key);
                if (actionSetId != null) {
                    results.add(persister.restoreActionSet(
                            ctx,
                            mctx,
                            actionSetId
                    ));
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            return persister.restoreActionSet(
                    ctx,
                    new MapDBCollectionPersistenceContext(db),
                    actionSetId
            );

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PropertyContainerSet saveActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            PropertyContainerSet tas = new PropertyContainerSet(UUID.randomUUID().toString(), null);
            List<PropertyContainer> al = new ArrayList<>();
            for (PropertyContainer ta : actions) {
                al.add(ta);
            }
            tas.setProperties(al);

            persister.saveActionSet(
                    ctx,
                    new MapDBCollectionPersistenceContext(db),
                    tas
            );

            return tas;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deleteActionSet(String actionSetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        db.close();
    }
}
