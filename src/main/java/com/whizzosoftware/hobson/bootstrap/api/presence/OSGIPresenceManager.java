/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.presence;

import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.event.EventListener;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.presence.*;

import java.util.*;

/**
 * An OSGi implementation of PresenceManager.
 *
 * TODO: This is current just an in-memory implementation for testing purposes.
 *
 * @author Dan Noguerol
 */
public class OSGIPresenceManager implements PresenceManager, EventListener {
    private Map<PresenceEntityContext,MutablePresenceEntity> entities = new HashMap<>();
    private Map<PresenceLocationContext,PresenceLocation> locations = new HashMap<>();
    private Map<PresenceEntityContext,PresenceLocationContext> entityLocations = new HashMap<>();

    private volatile EventManager eventManager;

    public void start() {
        eventManager.addListener(HubContext.createLocal(), this, new String[] {EventTopics.PRESENCE_TOPIC});
    }

    public void stop() {
        eventManager.removeListener(HubContext.createLocal(), this, new String[]{EventTopics.PRESENCE_TOPIC});
    }

    @Override
    public Collection<PresenceEntity> getAllEntities(HubContext ctx) {
        List<PresenceEntity> results = new ArrayList<>();
        results.addAll(entities.values());
        return results;
    }

    @Override
    public PresenceEntity getEntity(PresenceEntityContext ctx) {
        return entities.get(ctx);
    }

    @Override
    public PresenceEntityContext addEntity(HubContext hctx, String name) {
        PresenceEntityContext pec = PresenceEntityContext.create(hctx, UUID.randomUUID().toString());
        MutablePresenceEntity entity = new MutablePresenceEntity(pec, name);
        entities.put(pec, entity);
        return pec;
    }

    @Override
    public void deleteEntity(PresenceEntityContext ctx) {
        entities.remove(ctx);
    }

    @Override
    public PresenceLocation getEntityLocation(PresenceEntityContext ctx) {
        return getLocation(entityLocations.get(ctx));
    }

    @Override
    public void updateEntityLocation(PresenceEntityContext ectx, PresenceLocationContext newLocation) {
        PresenceLocationContext oldLocation = entityLocations.get(ectx);
        if (newLocation != null) {
            entityLocations.put(ectx, newLocation);
        } else {
            entityLocations.remove(ectx);
        }
        entities.get(ectx).setLastUpdate(System.currentTimeMillis());
        eventManager.postEvent(ectx.getHubContext(), new PresenceUpdateNotificationEvent(System.currentTimeMillis(), ectx, oldLocation, newLocation));
    }

    @Override
    public Collection<PresenceLocation> getAllLocations(HubContext ctx) {
        return locations.values();
    }

    @Override
    public PresenceLocation getLocation(PresenceLocationContext ctx) {
        return locations.get(ctx);
    }

    @Override
    public PresenceLocationContext addLocation(HubContext hctx, String name, Double latitude, Double longitude, Double radius, Integer beaconMajor, Integer beaconMinor) {
        PresenceLocationContext ctx = PresenceLocationContext.create(hctx, UUID.randomUUID().toString());
        PresenceLocation pl;
        if (beaconMajor != null && beaconMinor != null) {
            pl = new PresenceLocation(ctx, name, beaconMajor, beaconMinor);
        } else {
            pl = new PresenceLocation(ctx, name, latitude, longitude, radius);
        }
        locations.put(ctx, pl);
        return ctx;
    }

    @Override
    public void deleteLocation(PresenceLocationContext ctx) {
        locations.remove(ctx);
    }

    @Override
    public void onHobsonEvent(HobsonEvent event) {
        if (event != null && event instanceof PresenceUpdateRequestEvent) {
            PresenceUpdateRequestEvent pure = (PresenceUpdateRequestEvent)event;
            updateEntityLocation(pure.getEntityContext(), pure.getLocation());
        }
    }

    private class MutablePresenceEntity extends PresenceEntity {
        public MutablePresenceEntity(PresenceEntityContext ctx, String name) {
            super(ctx, name);
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }
}
