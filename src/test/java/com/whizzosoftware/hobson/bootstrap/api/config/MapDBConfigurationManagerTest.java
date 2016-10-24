/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.config;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.*;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        mgr.setLocalPluginConfiguration(pc, config);

        config = mgr.getLocalPluginConfiguration(pc, pcc);
        assertEquals("bar", config.getPropertyValue("foo"));

        config.setPropertyValue("bar", "foo");
        mgr.setLocalPluginConfiguration(pc, config);
        config = mgr.getLocalPluginConfiguration(pc, pcc);
        assertEquals("bar", config.getPropertyValue("foo"));
    }

    @Test
    public void testSetTwoIndividualConfigurationProperties() throws Exception {
        File file = File.createTempFile("foo", ".db");
        file.deleteOnExit();
        MapDBConfigurationManager mgr = new MapDBConfigurationManager(file);

        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");
        PropertyContainerClass pcc = new PropertyContainerClass(PropertyContainerClassContext.create(dctx, "configuration"), PropertyContainerClassType.DEVICE_CONFIG);
        pcc.addSupportedProperty(new TypedProperty.Builder("foo", "foo", "foo", TypedProperty.Type.STRING).build());
        pcc.addSupportedProperty(new TypedProperty.Builder("bar", "bar", "bar", TypedProperty.Type.STRING).build());

        mgr.setDeviceConfigurationProperty(dctx, pcc, "foo", "bar");
        mgr.setDeviceConfigurationProperty(dctx, pcc, "bar", "foo");

        PropertyContainer pc = mgr.getDeviceConfiguration(dctx, pcc);
        assertTrue(pc.hasPropertyValue("bar"));
        assertTrue(pc.hasPropertyValue("foo"));
    }
}
