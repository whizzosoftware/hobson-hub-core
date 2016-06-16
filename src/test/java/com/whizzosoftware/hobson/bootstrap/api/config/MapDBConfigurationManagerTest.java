/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.config;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassType;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class MapDBConfigurationManagerTest {
    @Test
    public void testSetHubConfiguration() throws Exception {
        File file = File.createTempFile("foo", ".db");
        file.deleteOnExit();
        MapDBConfigurationManager mgr = new MapDBConfigurationManager(file);

        PropertyContainer config = new PropertyContainer();
        config.setPropertyValue("adminPassword", "foo");
        mgr.setHubConfiguration(HubContext.createLocal(), config);

        config = mgr.getHubConfiguration(HubContext.createLocal());
        assertEquals("foo", config.getPropertyValue("adminPassword"));

        config.setPropertyValue("foo", "bar");
        mgr.setHubConfiguration(HubContext.createLocal(), config);
        config = mgr.getHubConfiguration(HubContext.createLocal());
        assertEquals("foo", config.getPropertyValue("adminPassword"));
    }

    @Test
    public void testSetLocalPluginConfiguration() throws Exception {
        File file = File.createTempFile("foo", ".db");
        file.deleteOnExit();
        MapDBConfigurationManager mgr = new MapDBConfigurationManager(file);

        PluginContext pc = PluginContext.createLocal("plugin1");
        PropertyContainerClass pcc = new PropertyContainerClass(PropertyContainerClassContext.create(pc, "configuration"), PropertyContainerClassType.PLUGIN_CONFIG);

        PropertyContainer config = new PropertyContainer();
        config.setPropertyValue("foo", "bar");
        mgr.setLocalPluginConfiguration(pc, pcc, config);

        config = mgr.getLocalPluginConfiguration(pc, pcc);
        assertEquals("bar", config.getPropertyValue("foo"));

        config.setPropertyValue("bar", "foo");
        mgr.setLocalPluginConfiguration(pc, pcc, config);
        config = mgr.getLocalPluginConfiguration(pc, pcc);
        assertEquals("bar", config.getPropertyValue("foo"));
    }
}
