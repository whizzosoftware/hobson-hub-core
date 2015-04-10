/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.presence;

import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.PresenceUpdateEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.presence.PresenceEntity;
import com.whizzosoftware.hobson.api.presence.PresenceEntityContext;
import com.whizzosoftware.hobson.api.presence.PresenceManager;

import java.util.*;

/**
 * An OSGi implementation of PresenceManager.
 *
 * TODO: This is current just an in-memory implementation for testing purposes.
 *
 * @author Dan Noguerol
 */
public class OSGIPresenceManager implements PresenceManager {
    private Map<String,MutablePresenceEntity> entities = new HashMap<>();

    private volatile EventManager eventManager;

    @Override
    public Collection<PresenceEntity> getAllEntities(HubContext ctx) {
        List<PresenceEntity> results = new ArrayList<>();
        results.addAll(entities.values());
        return results;
    }

    @Override
    public PresenceEntity getEntity(PresenceEntityContext ctx) {
        return entities.get(ctx.getEntityId());
    }

    @Override
    public String addEntity(PresenceEntity entity) {
        entities.put(entity.getContext().getEntityId(), new MutablePresenceEntity(entity));
        return entity.getContext().getEntityId();
    }

    @Override
    public void updateEntity(PresenceEntityContext ctx, String name, String location) {
        MutablePresenceEntity entity = entities.get(ctx.getEntityId());
        if (entity != null) {
            if (name != null) {
                entity.setName(name);
            }
            if (location != null) {
                entity.setLocation(location);
            }
        }
        eventManager.postEvent(ctx.getHubContext(), new PresenceUpdateEvent(System.currentTimeMillis(), ctx.getEntityId(), location));
    }
}
