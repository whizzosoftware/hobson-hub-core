package com.whizzosoftware.hobson.bootstrap.api.device.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.HobsonDeviceDescriptor;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.persist.ContextPathIdProvider;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.bootstrap.util.MapDBCollectionPersistenceContext;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapDBDeviceStore implements DeviceStore {
    private DB db;
    private IdProvider idProvider = new ContextPathIdProvider();
    private CollectionPersister persister = new CollectionPersister(idProvider);

    public MapDBDeviceStore(File file) {
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
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(HubContext hctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : ctx.getSet(idProvider.createDevicesId(hctx))) {
                String key = (String)o;
                HobsonDeviceDescriptor db = persister.restoreDevice(ctx, idProvider.createDeviceContext(key));
                if (db != null) {
                    results.add(db);
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<HobsonDeviceDescriptor> getAllDevices(PluginContext pctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonDeviceDescriptor> results = new ArrayList<>();
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            for (Object o : ctx.getSet(idProvider.createPluginDevicesId(pctx))) {
                String key = (String)o;
                HobsonDeviceDescriptor db = persister.restoreDevice(ctx, idProvider.createDeviceContext(key));
                if (db != null) {
                    results.add(db);
                }
            }
            return results;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public boolean hasDevice(DeviceContext ctx) {
        return (getDevice(ctx) != null); // TODO: inefficient
    }

    @Override
    public HobsonDeviceDescriptor getDevice(DeviceContext dctx) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            return persister.restoreDevice(ctx, dctx);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    synchronized public void saveDevice(HobsonDeviceDescriptor device) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);
            persister.saveDevice(ctx, device);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    synchronized public void deleteDevice(DeviceContext ctx) {

    }
}
