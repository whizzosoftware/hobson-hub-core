/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.user;

import com.google.inject.Inject;
import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.user.UserStore;
import com.whizzosoftware.hobson.rest.HobsonRole;
import com.whizzosoftware.hobson.rest.TokenHelper;
import com.whizzosoftware.hobson.rest.oidc.OIDCConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class LocalUserStore implements UserStore {
    private Logger logger = LoggerFactory.getLogger(LocalUserStore.class);

    @Inject
    HubManager hubManager;
    @Inject
    OIDCConfigProvider oidcConfigProvider;

    @Override
    public boolean hasDefaultUser() {
        return true;
    }

    @Override
    public String getDefaultUser() {
        return "local";
    }

    @Override
    public UserAuthentication authenticate(String username, String password) {
        if (hubManager.getLocalManager() != null && hubManager.getLocalManager().authenticateLocal(HubContext.createLocal(), password)) {
            HobsonUser user = createLocalUser();
            return new UserAuthentication(user, TokenHelper.createToken(oidcConfigProvider, user, HobsonRole.USER.value(), Collections.singletonList(HubContext.DEFAULT_HUB)));
        } else {
            throw new HobsonAuthenticationException("The authentication credentials are invalid");
        }
    }

    HobsonUser createLocalUser() {
        return new HobsonUser.Builder("local")
            .givenName("Local")
            .familyName("User")
            .build();
    }
}
