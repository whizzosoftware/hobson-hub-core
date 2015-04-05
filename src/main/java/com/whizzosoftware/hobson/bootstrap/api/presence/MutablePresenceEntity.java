/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.presence;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.presence.PresenceEntity;

/**
 * An implementation of PresenceEntity that allows changing its name and location.
 *
 * @author Dan Noguerol
 */
public class MutablePresenceEntity extends PresenceEntity {
    public MutablePresenceEntity(HubContext ctx, String name, String location) {
        super(ctx, name, location);
    }

    public MutablePresenceEntity(PresenceEntity entity) {
        super(entity.getContext().getHubContext(), entity.getName(), entity.getLocation());
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocation(String location) {
        this.location = location;
        this.lastUpdate = System.currentTimeMillis();
    }
}
