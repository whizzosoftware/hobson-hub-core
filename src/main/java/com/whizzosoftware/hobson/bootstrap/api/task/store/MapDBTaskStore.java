/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.TaskHelper;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.store.TaskStore;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * An implementation of TaskStore that uses MapDB for persistent storage.
 *
 * @author Dan Noguerol
 */
public class MapDBTaskStore implements TaskStore {
    private static final Logger logger = LoggerFactory.getLogger(MapDBTaskStore.class);

    private DB db;
    private IdProvider idProvider = new ContextPathIdProvider();
    private CollectionPersister persister = new CollectionPersister(idProvider);

    public MapDBTaskStore(File file) {
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
    public Collection<HobsonTask> getAllTasks(HubContext hctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonTask> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : ctx.getSet(idProvider.createTasksId(hctx))) {
                TaskContext tctx = TaskContext.create(hctx, (String)o);
                HobsonTask task = persister.restoreTask(ctx, tctx);
                if (task != null) {
                    results.add(task);
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<HobsonTask> getAllTasks(TaskManager taskManager, PluginContext pctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonTask> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : ctx.getSet(idProvider.createTasksId(pctx.getHubContext()))) {
                TaskContext tctx = TaskContext.create(pctx.getHubContext(), (String)o);
                HobsonTask task = persister.restoreTask(ctx, tctx);
                if (task != null && task.hasConditions()) {
                    if (TaskHelper.getTriggerCondition(taskManager, task.getConditions()).getContainerClassContext().getPluginContext().equals(pctx)) {
                        results.add(task);
                    }
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public HobsonTask getTask(TaskContext context) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            return persister.restoreTask(new MapDBCollectionPersistenceContext(db), context);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public HobsonTask saveTask(HobsonTask task) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding task: {}", task.getContext().toString());
            persister.saveTask(new MapDBCollectionPersistenceContext(db), task);
            return task;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deleteTask(TaskContext context) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Deleting task: {}", context.toString());
            persister.deleteTask(new MapDBCollectionPersistenceContext(db), context);
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
            for (Object o : mctx.getSet(idProvider.createActionSetsId(ctx))) {
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
