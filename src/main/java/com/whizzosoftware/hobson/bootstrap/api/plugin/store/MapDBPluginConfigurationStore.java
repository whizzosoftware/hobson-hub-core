/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.store;

import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.Collections;

/**
 * A MapDB implementation of PluginConfigurationStore.
 *
 * @author Dan Noguerol
 */
public class MapDBPluginConfigurationStore implements PluginConfigurationStore {
    private CollectionPersister persister;
    private CollectionPersistenceContext cpctx;

    public MapDBPluginConfigurationStore(File file) {
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
}
