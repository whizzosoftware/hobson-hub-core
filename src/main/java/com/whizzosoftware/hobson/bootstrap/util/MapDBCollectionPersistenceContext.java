/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import org.mapdb.DB;

import java.util.*;

/**
 * A MapDB implementation of CollectionPersistenceContext.
 *
 * @author Dan Noguerol
 */
public class MapDBCollectionPersistenceContext implements CollectionPersistenceContext {
    private DB db;

    public MapDBCollectionPersistenceContext(DB db) {
        this.db = db;
    }

    @Override
    public void addSetValue(String key, Object value) {
        Set<Object> s = getSet(key);
        s.add(value);
    }

    @Override
    public Map<String, Object> getMap(String key) {
        return db.createHashMap(key).makeOrGet();
    }

    @Override
    public Object getMapValue(String key, String name) {
        Map map = getMap(key);
        return map.get(name);
    }

    @Override
    public Set<Object> getSet(String key) {
        return db.createHashSet(key).makeOrGet();
    }

    @Override
    public boolean hasMap(String key) {
        return db.exists(key);
    }

    @Override
    public boolean hasSet(String key) {
        return db.exists(key);
    }

    @Override
    public boolean hasSetValue(String key, Object value) {
        Set<Object> s = getSet(key);
        return (s != null && s.contains(value));
    }

    @Override
    public void setMap(String key, Map<String,Object> map) {
        Map<String,Object> m = db.createHashMap(key).makeOrGet();
        m.clear();
        for (String k : map.keySet()) {
            m.put(k, map.get(k));
        }
    }

    @Override
    public void setMapValue(String key, String name, Object value) {
        Map<String,Object> map = getMap(key);
        map.put(name, value);
    }

    @Override
    public void setSet(String key, Set<Object> set) {
        Set<Object> s = getSet(key);
        s.clear();
        for (Object v : set) {
            s.add(v);
        }
    }

    @Override
    public void remove(String key) {
        db.delete(key);
    }

    @Override
    public void removeFromSet(String key, Object value) {
        Set<Object> set = getSet(key);
        set.remove(value);
    }

    @Override
    public void commit() {
        db.commit();
    }
}
