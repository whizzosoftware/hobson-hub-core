/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.presence.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.presence.PresenceEntity;
import com.whizzosoftware.hobson.api.presence.PresenceEntityContext;
import com.whizzosoftware.hobson.api.presence.PresenceLocation;
import com.whizzosoftware.hobson.api.presence.PresenceLocationContext;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.Collection;

public class MapDBPresenceStoreTest {
    @Test
    public void testAddAndDeletePresenceEntity()  throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MapDBPresenceStore store = new MapDBPresenceStore(dbFile);

        PresenceEntityContext pectx = PresenceEntityContext.createLocal("entity1");
        PresenceEntity pe = new PresenceEntity(pectx, "John Doe", 100l);

        store.savePresenceEntity(pe);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        // check that an entity is returned in the "get all" results
        Collection<PresenceEntity> entities = store.getAllPresenceEntities(HubContext.createLocal());
        assertEquals(1, entities.size());
        PresenceEntity pe2 = entities.iterator().next();
        assertEquals("local", pe2.getContext().getUserId());
        assertEquals("local", pe2.getContext().getHubId());
        assertEquals("entity1", pe2.getContext().getEntityId());
        assertEquals("John Doe", pe2.getName());
        assertEquals(100l, (long)pe2.getLastUpdate());

        // check that we can get the entity directly
        pe2 = store.getPresenceEntity(pectx);
        assertEquals("local", pe2.getContext().getUserId());
        assertEquals("local", pe2.getContext().getHubId());
        assertEquals("entity1", pe2.getContext().getEntityId());
        assertEquals("John Doe", pe2.getName());
        assertEquals(100l, (long)pe2.getLastUpdate());

        // delete the entity
        store.deletePresenceEntity(pectx);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        entities = store.getAllPresenceEntities(HubContext.createLocal());
        assertEquals(0, entities.size());
        pe2 = store.getPresenceEntity(pectx);
        assertNull(pe2);
    }

    @Test
    public void testAddPresenceEntityWithNullLastUpdate() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MapDBPresenceStore store = new MapDBPresenceStore(dbFile);

        PresenceEntityContext pectx = PresenceEntityContext.createLocal("entity1");
        PresenceEntity pe = new PresenceEntity(pectx, "John Doe", null);

        store.savePresenceEntity(pe);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        // check that we can get the entity directly
        PresenceEntity pe2 = store.getPresenceEntity(pectx);
        assertEquals("local", pe2.getContext().getUserId());
        assertEquals("local", pe2.getContext().getHubId());
        assertEquals("entity1", pe2.getContext().getEntityId());
        assertEquals("John Doe", pe2.getName());
        assertNull(pe2.getLastUpdate());
    }

    @Test
    public void testAddAndDeleteMapPresenceLocation()  throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MapDBPresenceStore store = new MapDBPresenceStore(dbFile);

        PresenceLocationContext plctx = PresenceLocationContext.createLocal("loc1");
        PresenceLocation pl = new PresenceLocation(plctx, "Home", 1.0, 2.0, 3.0);

        store.savePresenceLocation(pl);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        // check that an entity is returned in the "get all" results
        Collection<PresenceLocation> locations = store.getAllPresenceLocations(HubContext.createLocal());
        assertEquals(1, locations.size());
        PresenceLocation pl2 = locations.iterator().next();
        assertEquals("local", pl2.getContext().getUserId());
        assertEquals("local", pl2.getContext().getHubId());
        assertEquals("loc1", pl2.getContext().getLocationId());
        assertEquals("Home", pl2.getName());
        assertEquals(1.0, pl2.getLatitude(), 0.0);
        assertEquals(2.0, pl2.getLongitude(), 0.0);
        assertEquals(3.0, pl2.getRadius(), 0.0);
        assertNull(pl2.getBeaconMajor());
        assertNull(pl2.getBeaconMinor());

        // check that we can get the entity directly
        pl2 = store.getPresenceLocation(plctx);
        assertEquals("local", pl2.getContext().getUserId());
        assertEquals("local", pl2.getContext().getHubId());
        assertEquals("loc1", pl2.getContext().getLocationId());
        assertEquals("Home", pl2.getName());
        assertEquals(1.0, pl2.getLatitude(), 0.0);
        assertEquals(2.0, pl2.getLongitude(), 0.0);
        assertEquals(3.0, pl2.getRadius(), 0.0);
        assertNull(pl2.getBeaconMajor());
        assertNull(pl2.getBeaconMinor());

        // delete the entity
        store.deletePresenceLocation(plctx);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        // check that the presence location has been deleted
        locations = store.getAllPresenceLocations(HubContext.createLocal());
        assertEquals(0, locations.size());
        pl2 = store.getPresenceLocation(plctx);
        assertNull(pl2);
    }

    @Test
    public void testAddAndDeleteBeaconPresenceLocation()  throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MapDBPresenceStore store = new MapDBPresenceStore(dbFile);

        PresenceLocationContext plctx = PresenceLocationContext.createLocal("loc1");
        PresenceLocation pl = new PresenceLocation(plctx, "Home", 4, 5);

        store.savePresenceLocation(pl);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBPresenceStore(dbFile);

        PresenceLocation pl2 = store.getPresenceLocation(plctx);
        assertEquals("local", pl2.getContext().getUserId());
        assertEquals("local", pl2.getContext().getHubId());
        assertEquals("loc1", pl2.getContext().getLocationId());
        assertEquals("Home", pl2.getName());
        assertNull(pl2.getLatitude());
        assertNull(pl2.getLongitude());
        assertNull(pl2.getRadius());
        assertEquals(4, (int)pl2.getBeaconMajor());
        assertEquals(5, (int)pl2.getBeaconMinor());
    }
}
