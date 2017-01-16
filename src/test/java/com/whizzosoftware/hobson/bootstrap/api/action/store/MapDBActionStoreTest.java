/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.action.store;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapDBActionStoreTest {
    @Test
    public void testAddActionSet() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MapDBActionStore store = new MapDBActionStore(dbFile);

        List<PropertyContainer> actions = new ArrayList<>();
        actions.add(
                new PropertyContainer(
                    PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1"),
                    Collections.singletonMap("foo", (Object) "bar")
                )
        );
        String actionSetId = store.saveActionSet(HubContext.createLocal(), "actionSet1", actions).getId();

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBActionStore(dbFile);

        PropertyContainerSet pcs = store.getActionSet(HubContext.createLocal(), actionSetId);
        assertEquals(actionSetId, pcs.getId());
        assertTrue(pcs.hasProperties());
        assertEquals(1, pcs.getProperties().size());
        assertEquals("cc1", pcs.getProperties().get(0).getContainerClassContext().getContainerClassId());
        assertEquals("plugin1", pcs.getProperties().get(0).getContainerClassContext().getPluginId());
        assertTrue(pcs.getProperties().get(0).hasPropertyValues());
        assertEquals("bar", pcs.getProperties().get(0).getPropertyValue("foo"));
    }
}
