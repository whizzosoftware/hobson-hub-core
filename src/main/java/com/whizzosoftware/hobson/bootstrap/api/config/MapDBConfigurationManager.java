/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.config;

import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubConfigurationClass;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
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

    MapDBConfigurationManager(File file) {
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
    public PropertyContainer getHubConfiguration(HubContext ctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new PropertyContainer(
                PropertyContainerClassContext.create(ctx, HubConfigurationClass.ID),
                persister.restoreHubConfiguration(cpctx, ctx, PropertyContainerClassContext.create(ctx, HubConfigurationClass.ID))
            );
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
    public void setHubConfiguration(HubContext ctx, PropertyContainer config) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.saveHubConfiguration(cpctx, ctx, config.getPropertyValues());
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
    public PropertyContainer getLocalPluginConfiguration(PluginContext ctx, PropertyContainerClass configurationClass) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            return new PropertyContainer(
                    PropertyContainerClassContext.create(ctx, configurationClass.getContext().getContainerClassId()),
                    persister.restoreLocalPluginConfiguration(cpctx, ctx)
            );
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfiguration(PluginContext pctx, PropertyContainerClass configurationClass, PropertyContainer newConfig) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.deleteLocalPluginConfiguration(cpctx, pctx, false);
            persister.saveLocalPluginConfiguration(cpctx, pctx, newConfig.getPropertyValues());
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void setLocalPluginConfigurationProperty(PluginContext ctx, PropertyContainerClass configurationClass, String name, Object value) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            persister.saveLocalPluginConfiguration(cpctx, ctx, Collections.singletonMap(name, value));
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public PropertyContainer getDeviceConfiguration(DeviceContext ctx, PropertyContainerClass configurationClass, String name) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            Map<String,Object> props = persister.restoreDeviceConfiguration(cpctx, ctx);

            // build a list of PropertyContainer objects
            PropertyContainer ci = new PropertyContainer();
            for (TypedProperty meta : configurationClass.getSupportedProperties()) {
                Object value = null;
                if (props != null) {
                    value = props.get(meta.getId());
                }

                // if the name property is null, use the default device name
                if ("name".equals(meta.getId()) && value == null) {
                    value = name;
                }

                ci.setPropertyValue(meta.getId(), value);
            }


            ci.setContainerClassContext(configurationClass.getContext());

            return ci;
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
    public void setDeviceConfigurationProperties(DeviceContext ctx, PropertyContainerClass configurationClass, String deviceName, Map<String, Object> values, boolean overwrite) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            persister.deleteDeviceConfiguration(cpctx, ctx, false);
            persister.saveDeviceConfiguration(cpctx, ctx, values);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
