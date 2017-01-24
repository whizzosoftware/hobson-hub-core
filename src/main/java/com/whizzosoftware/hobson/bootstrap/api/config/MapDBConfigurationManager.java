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
import com.whizzosoftware.hobson.api.hub.HubConfigurationClass;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A MapDB implementation of ConfigurationManager.
 *
 * @author Dan Noguerol
 */
public class MapDBConfigurationManager implements ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(MapDBConfigurationManager.class);

    private CollectionPersister persister;
    private CollectionPersistenceContext cpctx;

    public MapDBConfigurationManager() {
        this(new File(new File(System.getProperty(ConfigurationManager.HOBSON_HOME), "data"), "config"));
    }

    public MapDBConfigurationManager(File file) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            // make sure parent directory exists
            if (!file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    logger.error("Unable to create data directory: {}", file.getParentFile());
                }
            }

            // create the MapDB context
            this.cpctx = new MapDBCollectionPersistenceContext(DBMaker.newFileDB(file).closeOnJvmShutdown().make());
            this.persister = new CollectionPersister(new ContextPathIdProvider());
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Map<String,Object> getHubConfiguration(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new HashMap<>(persister.restoreHubConfiguration(cpctx, ctx, PropertyContainerClassContext.create(ctx, HubConfigurationClass.ID)));
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
            persister.saveHubConfiguration(cpctx, ctx, config);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void deleteHubConfiguration(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.deleteHubConfiguration(cpctx, ctx, true);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Map<String,Object> getLocalPluginConfiguration(PluginContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new HashMap<>(persister.restoreLocalPluginConfiguration(cpctx, ctx));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfiguration(PluginContext pctx, Map<String,Object> newConfig) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.deleteLocalPluginConfiguration(cpctx, pctx, false);
            persister.saveLocalPluginConfiguration(cpctx, pctx, newConfig);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfigurationProperty(PluginContext ctx, String name, Object value) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.saveLocalPluginConfiguration(cpctx, ctx, Collections.singletonMap(name, value));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Map<String,Object> getDeviceConfiguration(DeviceContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new HashMap<>(persister.restoreDeviceConfiguration(cpctx, ctx));
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
            persister.deleteDeviceConfiguration(cpctx, dctx, false);
            persister.saveDeviceConfiguration(cpctx, dctx, values);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
