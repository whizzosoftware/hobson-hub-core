package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaData;
import com.whizzosoftware.hobson.api.hub.EmailConfiguration;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.Message;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EmailActionTest {
    @Test
    public void testCreateMessage() throws Exception {
        EmailConfiguration config = new EmailConfiguration("localhost", false, null, null, "foo@bar.com");
        EmailAction a = new EmailAction("pluginId");

        Map<String,Object> p = new HashMap<>();

        try {
            a.createMessage(null, config, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailConfiguration.PROP_MAIL_SENDER, "foo@bar.com");

        try {
            a.createMessage(null, config, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.RECIPIENT_ADDRESS, "bar@foo.com");

        try {
            a.createMessage(null, config, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.SUBJECT, "Subject");

        try {
            a.createMessage(null, config, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.MESSAGE, "Message");

        Message m = a.createMessage(null, config, p);
        Assert.assertEquals(1, m.getFrom().length);
        Assert.assertEquals("foo@bar.com", m.getFrom()[0].toString());
        Assert.assertEquals(1, m.getAllRecipients().length);
        Assert.assertEquals("bar@foo.com", m.getAllRecipients()[0].toString());
        Assert.assertEquals("Subject", m.getSubject());
        Assert.assertEquals("Message", m.getContent());
    }
}
