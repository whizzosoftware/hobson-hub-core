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

/**
 * Created by dan on 12/2/14.
 */
public class EmailActionTest {
    @Test
    public void testConstructor() {
        EmailConfiguration config = new EmailConfiguration("localhost", false, null, null, "foo@bar.com");
        EmailAction a = new EmailAction("pluginId", config);
        assertEquals(3, a.getMetaData().size());
        for (ActionMetaData meta : a.getMetaData()) {
            Assert.assertTrue(
                    "recipientAddress".equals(meta.getId()) ||
                            "subject".equals(meta.getId()) ||
                            "message".equals(meta.getId())
            );
        }
    }

//    @Test
//    public void testCreateAndSendMessageWithSMTP() throws Exception {
//        EmailAction a = new EmailAction("pluginId");
//
//        Dictionary c = new Hashtable();
//        Map<String,Object> p = new HashMap<String,Object>();
//        p.put(EmailAction.SENDER_ADDRESS, "foo@bar.com");
//        p.put(EmailAction.RECIPIENT_ADDRESS, "bar@foo.com");
//        p.put(EmailAction.SUBJECT, "Subject");
//        p.put(EmailAction.MESSAGE, "Message");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_SMTP_SERVER, "fafifwefnfdddsd");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_USE_SMTPS, false);
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (MessagingException ignored) {}
//    }
//
//    @Test
//    public void testCreateAndSendMessageWithSMTPS() throws Exception {
//        EmailAction a = new EmailAction("pluginId");
//
//        Dictionary c = new Hashtable();
//        Map<String,Object> p = new HashMap<String,Object>();
//        p.put(EmailAction.SENDER_ADDRESS, "foo@bar.com");
//        p.put(EmailAction.RECIPIENT_ADDRESS, "bar@foo.com");
//        p.put(EmailAction.SUBJECT, "Subject");
//        p.put(EmailAction.MESSAGE, "Message");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_SMTP_SERVER, "fafifwefnfdddsd");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_USE_SMTPS, true);
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_USER, "user");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (HobsonRuntimeException ignored) {}
//
//        c.put(EmailAction.PROP_PASSWORD, "password");
//
//        try {
//            a.createAndSendMessage(c, p);
//        } catch (MessagingException ignored) {}
//    }

    @Test
    public void testCreateMessage() throws Exception {
        EmailAction a = new EmailAction("pluginId", new EmailConfiguration("localhost", false, null, null, "foo@bar.com"));

        Map<String,Object> p = new HashMap<>();

        try {
            a.createMessage(null, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailConfiguration.PROP_MAIL_SENDER, "foo@bar.com");

        try {
            a.createMessage(null, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.RECIPIENT_ADDRESS, "bar@foo.com");

        try {
            a.createMessage(null, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.SUBJECT, "Subject");

        try {
            a.createMessage(null, p);
            Assert.fail("Should have failed with exception");
        } catch (HobsonRuntimeException ignored) {}

        p.put(EmailAction.MESSAGE, "Message");

        Message m = a.createMessage(null, p);
        Assert.assertEquals(1, m.getFrom().length);
        Assert.assertEquals("foo@bar.com", m.getFrom()[0].toString());
        Assert.assertEquals(1, m.getAllRecipients().length);
        Assert.assertEquals("bar@foo.com", m.getAllRecipients()[0].toString());
        Assert.assertEquals("Subject", m.getSubject());
        Assert.assertEquals("Message", m.getContent());
    }
}
