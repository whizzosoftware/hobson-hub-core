/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.store;

import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import org.mapdb.DB;

import java.util.*;

/**
 * A MapDB implementation of CollectionPersistenceContext.
 *
 * @author Dan Noguerol
 */
public class MapDBCollectionPersistenceContext implements CollectionPersistenceContext {
    private DB db;
    private Map<String,Object> map;

    public MapDBCollectionPersistenceContext(DB db, String mapName) {
        this.db = db;
        this.map = db.getHashMap(mapName);
    }

    @Override
    public Map<String, Object> getMap(String key) {
        Map<String,Object> m = (Map<String,Object>)map.get(key);
        if (m == null) {
            m = new HashMap<>();
            map.put(key, m);
        }
        return m;
    }

    @Override
    public List<Map<String, Object>> getMapsWithPrefix(String keyPrefix) {
        List<Map<String,Object>> results = new ArrayList<>();
        for (String key : map.keySet()) {
            if (key.startsWith(keyPrefix)) {
                results.add((Map<String,Object>)map.get(key));
            }
        }
        return results;
    }

    @Override
    public void setMap(String key, Map<String,Object> map) {
        this.map.put(key, map);
    }

    @Override
    public Set<String> getKeySet() {
        return map.keySet();
    }

    @Override
    public void commit() {
        db.commit();
    }
}
