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
import com.whizzosoftware.hobson.api.persist.KeyUtil;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.TaskHelper;
import com.whizzosoftware.hobson.api.task.TaskManager;
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
    private TaskManager taskManager;
    private CollectionPersister persister = new CollectionPersister();

    public MapDBTaskStore(File file, TaskManager taskManager) {
        this.taskManager = taskManager;

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
    public Collection<HobsonTask> getAllTasks() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonTask> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db, "tasks");
            String keyPrefix = KeyUtil.createTaskMetaRootKey(HubContext.createLocal());
            for (String key : ctx.getKeySet()) {
                if (key.startsWith(keyPrefix)) {
                    Map<String,Object> taskMetaMap = ctx.getMap(key);
                    results.add(persister.restoreTask(ctx, TaskContext.create((String)taskMetaMap.get("context"))));
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
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db, "tasks");
            String keyPrefix = KeyUtil.createTaskMetaRootKey(HubContext.createLocal());
            for (String key : ctx.getKeySet()) {
                if (key.startsWith(keyPrefix)) {
                    Map<String,Object> taskMetaMap = ctx.getMap(key);
                    HobsonTask task = persister.restoreTask(ctx, TaskContext.create((String)taskMetaMap.get("context")));
                    if (task.hasConditions()) {
                        if (TaskHelper.getTriggerCondition(taskManager, task.getConditions()).getContainerClassContext().getPluginContext().equals(pctx)) {
                            results.add(task);
                        }
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

            return persister.restoreTask(new MapDBCollectionPersistenceContext(db, "tasks"), context);

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public HobsonTask addTask(HobsonTask task) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            logger.debug("Adding task: {}", task.getContext().toString());
            persister.saveTask(new MapDBCollectionPersistenceContext(db, "tasks"), task);
            return task;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deleteTask(TaskContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<PropertyContainerSet> results = new ArrayList<>();
            MapDBCollectionPersistenceContext mctx = new MapDBCollectionPersistenceContext(db, "actions");
            for (String key : mctx.getKeySet()) {
                String actionSetId = persister.getActionSetIdFromKey(ctx, key);
                if (actionSetId != null) {
                    results.add(persister.restoreActionSet(
                            ctx,
                            mctx,
                            taskManager,
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
                    new MapDBCollectionPersistenceContext(db, "actions"),
                    taskManager,
                    actionSetId
            );

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PropertyContainerSet addActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
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
                new MapDBCollectionPersistenceContext(db, "actions"),
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
