package com.whizzosoftware.hobson.bootstrap.util;

import org.junit.Test;
import static org.junit.Assert.*;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MapDBCollectionPersistenceContextTest {
    @Test
    public void testMapAndSet() throws Exception {
        File file = File.createTempFile("foo", "db");
        file.deleteOnExit();

        DB db = DBMaker.newFileDB(file).closeOnJvmShutdown().make();
        MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);

        // add a simple map
        Map<String,Object> map = new HashMap<>();
        map.put("foo", "bar");
        ctx.setMap("map1", map);

        // add a simple set
        Set<Object> set = new TreeSet<>();
        set.add("foo");
        ctx.setSet("set1", set);

        // commit
        ctx.commit();
        db.close();

        // create a new DB
        db = DBMaker.newFileDB(file).closeOnJvmShutdown().make();
        ctx = new MapDBCollectionPersistenceContext(db);

        // confirm the map was restored
        map = ctx.getMap("map1");
        assertNotNull(map);
        assertEquals(1, map.size());
        assertEquals("bar", map.get("foo"));

        // confirm the set was restored
        set = ctx.getSet("set1");
        assertNotNull(set);
        assertEquals(1, set.size());
        assertTrue(set.contains("foo"));
    }

    @Test
    public void testAddSetValue() throws Exception {
        File file = File.createTempFile("foo", "db");
        file.deleteOnExit();

        DB db = DBMaker.newFileDB(file).closeOnJvmShutdown().make();
        MapDBCollectionPersistenceContext ctx = new MapDBCollectionPersistenceContext(db);

        ctx.addSetValue("set1", "val1");
        ctx.addSetValue("set1", "val2");

        ctx.commit();
        db.close();

        db = DBMaker.newFileDB(file).closeOnJvmShutdown().make();
        ctx = new MapDBCollectionPersistenceContext(db);

        Set<Object> set = ctx.getSet("set1");
        assertNotNull(set);
        assertEquals(2, set.size());
        assertTrue(set.contains("val1"));
        assertTrue(set.contains("val2"));
    }
}
