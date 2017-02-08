/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.config;

import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.executor.ExecutorManager;
import com.whizzosoftware.hobson.api.hub.HubConfigurationClass;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A MapDB implementation of ConfigurationManager.
 *
 * @author Dan Noguerol
 */
public class MapDBConfigurationManager implements ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(MapDBConfigurationManager.class);

    @Inject
    volatile private ExecutorManager executorManager;

    private File dbFile;
    private DB db;
    private CollectionPersister persister;
    private CollectionPersistenceContext cpctx;
    private Future housekeepingFuture;

    public MapDBConfigurationManager() {
        this(new File(new File(System.getProperty(ConfigurationManager.HOBSON_HOME), "data"), "com.whizzosoftware.hobson.hub.hobson-hub-core$config"));
    }

    public MapDBConfigurationManager(File dbFile) {
        this.dbFile = dbFile;
    }

    public void start() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            // make sure parent directory exists
            if (!dbFile.getParentFile().exists()) {
                if (!dbFile.getParentFile().mkdirs()) {
                    logger.error("Unable to create data directory: {}", dbFile.getParentFile());
                }
            }

            // create the MapDB context
            this.db = DBMaker.newFileDB(dbFile).closeOnJvmShutdown().make();
            this.cpctx = new MapDBCollectionPersistenceContext(db);
            this.persister = new CollectionPersister(new ContextPathIdProvider());

            // create database compaction task (run it starting at random interval between 22 and 24 hours)
            if (executorManager != null) {
                housekeepingFuture = executorManager.schedule(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Performing config store housekeeping");
                        synchronized (db) {
                            try {
                                db.commit();
                                db.compact();
                            } catch (Throwable t) {
                                logger.error("Error compacting configuration database", t);
                            }
                        }
                    }
                }, 1440 - ThreadLocalRandom.current().nextInt(0, 121), 1440, TimeUnit.MINUTES);
            } else {
                logger.error("No executor manager available to perform configuration manager housekeeping");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public void stop() {
        if (executorManager != null && housekeepingFuture != null) {
            executorManager.cancel(housekeepingFuture);
        }
    }

    @Override
    public Map<String,Object> getHubConfiguration(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new TreeMap<>(persister.restoreHubConfiguration(cpctx, ctx, PropertyContainerClassContext.create(ctx, HubConfigurationClass.ID)));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Object getHubConfigurationProperty(HubContext ctx, String name) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return persister.restoreHubConfiguration(cpctx, ctx, PropertyContainerClassContext.create(ctx, HubConfigurationClass.ID)).get(name);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setHubConfiguration(HubContext ctx, Map<String,Object> config) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveHubConfiguration(cpctx, ctx, config, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deleteHubConfiguration(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.deleteHubConfiguration(cpctx, ctx, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Map<String,Object> getLocalPluginConfiguration(PluginContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new TreeMap<>(persister.restoreLocalPluginConfiguration(cpctx, ctx));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfiguration(PluginContext pctx, Map<String,Object> newConfig) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.deleteLocalPluginConfiguration(cpctx, pctx, false);
                persister.saveLocalPluginConfiguration(cpctx, pctx, newConfig, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfigurationProperty(PluginContext ctx, String name, Object value) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                persister.saveLocalPluginConfiguration(cpctx, ctx, Collections.singletonMap(name, value), true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Map<String,Object> getDeviceConfiguration(DeviceContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new TreeMap<>(persister.restoreDeviceConfiguration(cpctx, ctx));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Object getDeviceConfigurationProperty(DeviceContext ctx, String name) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Map<String,Object> map = persister.restoreDeviceConfiguration(cpctx, ctx);
            return map.get(name);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setDeviceConfigurationProperty(DeviceContext ctx, String name, Object value) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Map<String,Object> map = persister.restoreDeviceConfiguration(cpctx, ctx);
            Map<String,Object> newMap = new HashMap<>(map);
            newMap.put(name, value);
            setDeviceConfigurationProperties(ctx, newMap);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setDeviceConfigurationProperties(DeviceContext dctx, Map<String, Object> values) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            synchronized (db) {
                persister.deleteDeviceConfiguration(cpctx, dctx, false);
                persister.saveDeviceConfiguration(cpctx, dctx, values, true);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
