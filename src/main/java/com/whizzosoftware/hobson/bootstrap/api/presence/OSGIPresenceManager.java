/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.presence;

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.presence.PresenceEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateNotificationEvent;
import com.whizzosoftware.hobson.api.event.presence.PresenceUpdateRequestEvent;
import com.whizzosoftware.hobson.api.executor.ExecutorManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.*;
import com.whizzosoftware.hobson.api.presence.store.PresenceStore;
import com.whizzosoftware.hobson.bootstrap.api.presence.store.MapDBPresenceStore;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * An OSGi implementation of PresenceManager.
 *
 * @author Dan Noguerol
 */
public class OSGIPresenceManager implements PresenceManager {
    private static final Logger logger = LoggerFactory.getLogger(OSGIPresenceManager.class);

    @Inject
    private volatile PluginManager pluginManager;
    @Inject
    private volatile EventManager eventManager;
    @Inject
    private volatile ExecutorManager executorManager;

    private PresenceStore presenceStore;
    private Map<PresenceEntityContext,PresenceLocationContext> entityLocations = new HashMap<>();
    private Future housekeepingFuture;

    public void start() {
        // listen for presence events
        eventManager.addListener(HubContext.createLocal(), this);

        // if a task store hasn't already been injected, create a default one
        if (presenceStore == null) {
            this.presenceStore = new MapDBPresenceStore(
                pluginManager.getDataFile(
                    PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                    "presence"
                )
            );
        }

        // schedule housekeeping
        if (executorManager != null) {
            housekeepingFuture = executorManager.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        presenceStore.performHousekeeping();
                    } catch (Throwable t) {
                        logger.error("Error performing presence store housekeeping", t);
                    }
                }
            }, 1440 - ThreadLocalRandom.current().nextInt(0, 121), 1440, TimeUnit.MINUTES);
        } else {
            logger.error("No executor manager available to perform presence store housekeeping");
        }
    }

    public void stop() {
        if (housekeepingFuture != null && executorManager != null) {
            executorManager.cancel(housekeepingFuture);
        }

        eventManager.removeListener(HubContext.createLocal(), this);
    }

    @EventHandler
    public void handle(PresenceEvent event) {
        if (event != null && event instanceof PresenceUpdateRequestEvent) {
            PresenceUpdateRequestEvent pure = (PresenceUpdateRequestEvent)event;
            updatePresenceEntityLocation(pure.getEntityContext(), pure.getLocation());
        }
    }

    public void setPresenceStore(PresenceStore presenceStore) {
        this.presenceStore = presenceStore;
    }

    @Override
    public Collection<PresenceEntity> getAllPresenceEntities(HubContext ctx) {
        return presenceStore.getAllPresenceEntities(ctx);
    }

    @Override
    public PresenceEntity getPresenceEntity(PresenceEntityContext ctx) {
        return presenceStore.getPresenceEntity(ctx);
    }

    @Override
    public PresenceEntityContext addPresenceEntity(HubContext ctx, String name) {
        PresenceEntityContext pec = PresenceEntityContext.create(ctx, UUID.randomUUID().toString());
        presenceStore.savePresenceEntity(new PresenceEntity(pec, name));
        return pec;
    }

    @Override
    public void deletePresenceEntity(PresenceEntityContext ctx) {
        presenceStore.deletePresenceEntity(ctx);
    }

    @Override
    public PresenceLocation getPresenceEntityLocation(PresenceEntityContext ctx) {
        return presenceStore.getPresenceLocation(entityLocations.get(ctx));
    }

    @Override
    public void updatePresenceEntityLocation(PresenceEntityContext ectx, PresenceLocationContext newLocationCtx) {
        PresenceLocation oldLocation = presenceStore.getPresenceLocation(entityLocations.get(ectx));

        // update entity's location
        PresenceLocationContext oldLocationCtx = oldLocation != null ? oldLocation.getContext() : null;
        entityLocations.put(ectx, newLocationCtx);

        // update entity's last update time
        PresenceEntity pe = presenceStore.getPresenceEntity(ectx);
        presenceStore.savePresenceEntity(new PresenceEntity(ectx, pe.getName(), System.currentTimeMillis()));

        // post an update event
        eventManager.postEvent(ectx.getHubContext(), new PresenceUpdateNotificationEvent(System.currentTimeMillis(), ectx, oldLocationCtx, newLocationCtx));
    }

    @Override
    public Collection<PresenceLocation> getAllPresenceLocations(HubContext ctx) {
        return presenceStore.getAllPresenceLocations(ctx);
    }

    @Override
    public PresenceLocation getPresenceLocation(PresenceLocationContext ctx) {
        return presenceStore.getPresenceLocation(ctx);
    }

    @Override
    public PresenceLocationContext addPresenceLocation(HubContext hctx, String name, Double latitude, Double longitude, Double radius, Integer beaconMajor, Integer beaconMinor) {
        PresenceLocationContext ctx = PresenceLocationContext.create(hctx, UUID.randomUUID().toString());
        PresenceLocation pl;
        if (beaconMajor != null && beaconMinor != null) {
            pl = new PresenceLocation(ctx, name, beaconMajor, beaconMinor);
        } else {
            pl = new PresenceLocation(ctx, name, latitude, longitude, radius);
        }
        presenceStore.savePresenceLocation(pl);
        return ctx;
    }

    @Override
    public void deletePresenceLocation(PresenceLocationContext ctx) {
        presenceStore.deletePresenceLocation(ctx);
    }
}
