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
import com.whizzosoftware.hobson.api.presence.PresenceEntity;
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
    public Collection<PresenceEntity> getAllEntities(String userId, String hubId) {
        List<PresenceEntity> results = new ArrayList<>();
        results.addAll(entities.values());
        return results;
    }

    @Override
    public PresenceEntity getEntity(String userId, String hubId, String entityId) {
        return entities.get(entityId);
    }

    @Override
    public String addEntity(String userId, String hubId, PresenceEntity entity) {
        entities.put(entity.getId(), new MutablePresenceEntity(entity));
        return entity.getId();
    }

    @Override
    public void updateEntity(String userId, String hubId, String entityId, String name, String location) {
        MutablePresenceEntity entity = entities.get(entityId);
        if (entity != null) {
            if (name != null) {
                entity.setName(name);
            }
            if (location != null) {
                entity.setLocation(location);
            }
        }
        eventManager.postEvent(userId, hubId, new PresenceUpdateEvent(entityId, location));
    }
}
