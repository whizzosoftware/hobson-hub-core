/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.hub;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.EmailConfiguration;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.Message;

public class OSGIHubManagerTest {
    @Test
    public void testCreateMessage() throws Exception {
        EmailConfiguration config = new EmailConfiguration("localhost", false, null, null, "foo@bar.com");
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
        Assert.assertEquals(1, m.getFrom().length);
        Assert.assertEquals("foo@bar.com", m.getFrom()[0].toString());
        Assert.assertEquals(1, m.getAllRecipients().length);
        Assert.assertEquals("bar@foo.com", m.getAllRecipients()[0].toString());
        Assert.assertEquals("Subject", m.getSubject());
        Assert.assertEquals("Message", m.getContent());
    }
}
