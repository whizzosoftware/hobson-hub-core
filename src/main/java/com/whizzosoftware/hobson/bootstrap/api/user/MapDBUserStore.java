/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.user;

import com.google.inject.Inject;
import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.HobsonAuthorizationException;
import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.executor.ExecutorManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.hub.PasswordChange;
import com.whizzosoftware.hobson.api.user.HobsonRole;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.user.UserStore;
import com.whizzosoftware.hobson.rest.TokenHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MapDBUserStore implements UserStore {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String DEFAULT_USER = "admin";

    @Inject
    HubManager hubManager;

    private DB db;

    @Inject
    MapDBUserStore(ExecutorManager executorManager) {
        File f = new File(System.getProperty(ConfigurationManager.HOBSON_HOME, "."), "data");
        if (!f.exists()) {
            if (!f.mkdir()) {
                logger.error("Error creating data directory");
                return;
            }
        }

        setFile(new File(f, "com.whizzosoftware.hobson.hub.hobson-hub-core$users"));

        // create action store housekeeping task (run it starting at random interval between 22 and 24 hours)
        if (executorManager != null) {
            executorManager.schedule(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Running user store housekeeping");
                    try {
                        synchronized (db) {
                            db.commit();
                            db.compact();
                        }
                    } catch (Throwable t) {
                        logger.error("Error compacting user database", t);
                    }
                    System.out.println("User store housekeeping complete");
                }
            }, 1440 - ThreadLocalRandom.current().nextInt(0, 121), 1440, TimeUnit.MINUTES);
        } else {
            logger.error("No executor manager available to perform user store housekeeping");
        }
    }

    MapDBUserStore(File file) {
        setFile(file);
    }

    public void setHubManager(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    public void addUser(String username, String password, String givenName, String familyName, Collection<HobsonRole> roles) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            synchronized (db) {
                Map<String, Object> m = db.createTreeMap(username).makeOrGet();
                m.put("user", username);
                m.put("password", DigestUtils.sha256Hex(password));
                m.put("givenName", givenName);
                m.put("familyName", familyName);

                List<String> r = new ArrayList<>();
                for (HobsonRole hr : roles) {
                    r.add(hr.name());
                }
                m.put("roles", r);

                db.commit();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public UserAuthentication authenticate(String username, String password) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            Map<String,Object> map = db.getTreeMap(username);
            if (map != null) {
                String p = (String)map.get("password");
                if (p != null && p.equals(DigestUtils.sha256Hex(password))) {
                    HobsonUser user = createUser(username, map);
                    return new UserAuthentication(user, TokenHelper.createToken(hubManager.getOIDCConfiguration(), user, user.getRoles(), Collections.singletonList(HubContext.DEFAULT_HUB)));
                }
            }

            throw new HobsonAuthenticationException("Invalid username and/or password.");
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public void changeUserPassword(String username, PasswordChange change) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (db) {
                Map<String, Object> map = db.getTreeMap(username);
                if (map != null) {
                    String currentPassword = (String) map.get("password");
                    if (DigestUtils.sha256Hex(change.getCurrentPassword()).equals(currentPassword)) {
                        map.put("password", DigestUtils.sha256Hex(change.getNewPassword()));
                        db.commit();
                    } else {
                        throw new HobsonAuthorizationException("Unable to change user password");
                    }
                } else {
                    throw new HobsonAuthorizationException("Unable to change user password");
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    private HobsonUser createUser(String username, Map<String,Object> map) {
        return new HobsonUser.Builder(username).givenName((String)map.get("givenName")).familyName((String)map.get("familyName")).roles((List<String>)map.get("roles")).build();
    }

    @Override
    public String getDefaultUser() {
        return DEFAULT_USER;
    }

    @Override
    public Collection<String> getHubsForUser(String username) {
        return Collections.singletonList("local");
    }

    @Override
    public HobsonUser getUser(String username) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Map<String,Object> map = db.getTreeMap(username);
            return createUser(username, map);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public Collection<HobsonUser> getUsers() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            List<HobsonUser> users = new ArrayList<>();
            Map<String,Object> map = db.getAll();
            for (String username : map.keySet()) {
                users.add(createUser(username, (Map<String,Object>)map.get(username)));
            }
            return users;

        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public boolean hasDefaultUser() {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Map<String,Object> m = db.getAll();
            return (m.size() <= 1);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    public void setFile(File file) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            db = DBMaker.newFileDB(file)
                    .closeOnJvmShutdown()
                    .make();

            synchronized (db) {
                Map<String, Object> m = db.createTreeMap("admin").makeOrGet();
                if (!m.containsKey("password")) {
                    m.put("user", DEFAULT_USER);
                    m.put("password", DigestUtils.sha256Hex("password"));
                    m.put("givenName", "Administrator");
                    m.put("familyName", "User");
                    m.put("roles", Collections.singletonList(HobsonRole.administrator.name()));
                    db.commit();
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public boolean supportsUserManagement() {
        return true;
    }
}
