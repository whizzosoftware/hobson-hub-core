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
import org.junit.Assert;
import org.junit.Test;

import javax.mail.Message;

import static org.junit.Assert.assertEquals;
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
}
