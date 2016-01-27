/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.user;

import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.HobsonAuthorizationException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.api.user.UserStore;

import java.util.Collection;
import java.util.Collections;

public class LocalUserStore implements UserStore {
    private HubManager hubManager;

    public LocalUserStore(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    public boolean hasDefaultUser() {
        return true;
    }

    @Override
    public String getDefaultUser() {
        return "local";
    }

    @Override
    public HobsonUser authenticate(String username, String password) {
        if (hubManager.getLocalManager() != null && hubManager.getLocalManager().authenticateLocal(HubContext.createLocal(), password)) {
            return createLocalUser();
        } else {
            throw new HobsonAuthenticationException("The authentication credentials are invalid");
        }
    }

    @Override
    public HobsonUser getUser(String username) {
        if (username.equals("local")) {
            return createLocalUser();
        } else {
            throw new HobsonAuthorizationException("You are not authorized to retrieve that information");
        }
    }

    @Override
    public Collection<String> getUserIds() {
        return Collections.singletonList("local");
    }

    protected HobsonUser createLocalUser() {
        return new HobsonUser.Builder("local")
            .givenName("Local")
            .familyName("User")
            .build();
    }
}
