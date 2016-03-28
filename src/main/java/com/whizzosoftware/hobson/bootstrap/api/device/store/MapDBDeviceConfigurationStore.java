/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.Map;

/**
 * MapDB implementation of DeviceConfigurationStore.
 *
 * @author Dan Noguerol
 */
public class MapDBDeviceConfigurationStore implements DeviceConfigurationStore {
    private CollectionPersister persister;
    private CollectionPersistenceContext cpctx;

    public MapDBDeviceConfigurationStore(File file) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            this.cpctx = new MapDBCollectionPersistenceContext(DBMaker.newFileDB(file).closeOnJvmShutdown().make());
            this.persister = new CollectionPersister(new ContextPathIdProvider());
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public void close() {
        cpctx.close();
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
