/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.hub;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubConfigurationClass;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.Message;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class OSGIHubManagerTest {
    @Test
    public void testGetConfiguration() {
        OSGIHubManager hm = new OSGIHubManager();
        hm.start();
        PropertyContainer pc = hm.getConfiguration(HubContext.createLocal());
        assertNotNull(pc);

        pc = hm.getHub(HubContext.createLocal()).getConfiguration();
        assertNotNull(pc);
    }

    @Test
    public void testGetConfigurationClass() {
        OSGIHubManager hm = new OSGIHubManager();
        hm.start();
        PropertyContainerClass pcc = hm.getConfigurationClass(HubContext.createLocal());
        assertNotNull(pcc);
        assertEquals(12, pcc.getSupportedProperties().size());
    }

    @Test
    public void testCreateMessage() throws Exception {
        PropertyContainer config = new PropertyContainer();
        config.setPropertyValue(HubConfigurationClass.EMAIL_SERVER, "localhost");
        config.setPropertyValue(HubConfigurationClass.EMAIL_SECURE, false);
        config.setPropertyValue(HubConfigurationClass.EMAIL_SENDER, "foo@bar.com");
        OSGIHubManager a = new OSGIHubManager();

        try {
            a.createMessage(null, config, null, null, null);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        try {
            a.createMessage(null, config, "bar@foo.com", null, null);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        try {
            a.createMessage(null, config, "bar@foo.com", "Subject", null);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        Message m = a.createMessage(null, config, "bar@foo.com", "Subject", "Message");
        assertEquals(1, m.getFrom().length);
        assertEquals("foo@bar.com", m.getFrom()[0].toString());
        assertEquals(1, m.getAllRecipients().length);
        assertEquals("bar@foo.com", m.getAllRecipients()[0].toString());
        assertEquals("Subject", m.getSubject());
        assertEquals("Message", m.getContent());
    }

    @Test
    public void testGetLogWithInvalidJSONLines() throws Exception {
        File file = File.createTempFile("test", "log");
        String path = file.getAbsolutePath();
        file.deleteOnExit();

        FileWriter writer = new FileWriter(path);
        writer.write("{\"time\":\"1485891491094\",\"thread\":\"localEventLoopGroup-4-1\",\"level\":\"ERROR\",\"message\":\"Test!\"}\n");
        writer.write("----\n");
        writer.close();

        OSGIHubManager a = new OSGIHubManager();
        StringBuilder appendable = new StringBuilder();
        a.getLog(HubContext.createLocal(), path, 0, 2, appendable);

        JSONArray array = new JSONArray(new JSONTokener(appendable.toString()));
        assertEquals(2, array.length());
        JSONObject json = array.getJSONObject(0).getJSONObject("item");
        assertFalse(json.has("time"));
        assertFalse(json.has("thread"));
        assertFalse(json.has("level"));
        assertEquals("----", json.getString("message"));

        json = array.getJSONObject(1).getJSONObject("item");
        assertEquals("1485891491094", json.getString("time"));
        assertEquals("localEventLoopGroup-4-1", json.getString("thread"));
        assertEquals("ERROR", json.getString("level"));
        assertEquals("Test!", json.getString("message"));
    }
}
