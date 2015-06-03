package com.whizzosoftware.hobson.bootstrap.api.user;

import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.HobsonAuthorizationException;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.api.user.UserStore;

public class LocalUserStore implements UserStore {
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
        if (username.equals("local") && password.equals("local")) {
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

    protected HobsonUser createLocalUser() {
        return new HobsonUser.Builder()
            .id("local")
            .firstName("Local")
            .lastName("User")
            .build();
    }
}
