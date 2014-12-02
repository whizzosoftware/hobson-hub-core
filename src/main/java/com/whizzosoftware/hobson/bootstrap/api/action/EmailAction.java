/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.AbstractHobsonAction;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaData;
import com.whizzosoftware.hobson.api.hub.EmailConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;

/**
 * A HobsonAction implementation that sends e-mails.
 *
 * @author Dan Noguerol
 * @since hobson-hub-api 0.1.6
 */
public class EmailAction extends AbstractHobsonAction {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final String RECIPIENT_ADDRESS = "recipientAddress";
    public static final String SUBJECT = "subject";
    public static final String MESSAGE = "message";

    private EmailConfiguration config;

    /**
     * Constructor.
     *
     * @param pluginId the plugin ID creating this action
     *
     * @since hobson-hub-api 0.1.6
     */
    public EmailAction(String pluginId, EmailConfiguration config) {
        super(pluginId, "sendEmail", "Send E-mail");

        this.config = config;

        addMetaData(new ActionMetaData(RECIPIENT_ADDRESS, "Recipient Address", "The address the e-mail will be sent to", ActionMetaData.Type.STRING));
        addMetaData(new ActionMetaData(SUBJECT, "Subject", "The e-mail subject text", ActionMetaData.Type.STRING));
        addMetaData(new ActionMetaData(MESSAGE, "Message Body", "The e-mail message body", ActionMetaData.Type.STRING));
    }

    /**
     * Executes the action.
     *
     * @param properties a Map of action properties
     *
     * @since hobson-hub-api 0.1.6
     */
    @Override
    public void execute(Map<String, Object> properties) {
        try {
            // get the mail configuration
            createAndSendMessage(properties);
        } catch (MessagingException e){
            throw new HobsonRuntimeException("Error sending e-mail", e);
        }
    }

    /**
     * Convenience method to both create and send the e-mail message.
     *
     * @param properties the message properties
     *
     * @throws MessagingException on failure
     *
     * @since hobson-hub-api 0.1.6
     */
    protected void createAndSendMessage(Map<String,Object> properties) throws MessagingException {
        String mailHost = config.getMailServer();
        Boolean mailUseSMTPS = config.isSMTPS();
        String mailUser = config.getUsername();
        String mailPassword = config.getPassword();

        if (mailHost == null) {
            throw new HobsonRuntimeException("No mail host is configured; unable to execute e-mail action");
        } else if (mailUseSMTPS && mailUser == null) {
            throw new HobsonRuntimeException("No mail server username is configured for SMTPS; unable to execute e-mail action");
        } else if (mailUseSMTPS && mailPassword == null) {
            throw new HobsonRuntimeException("No mail server password is configured for SMTPS; unable to execute e-mail action");
        }

        // create mail session
        Properties props = new Properties();
        props.put("mail.smtp.host", mailHost);
        Session session = Session.getDefaultInstance(props, null);

        // create the mail message
        Message msg = createMessage(session, properties);

        // send the message
        String protocol = mailUseSMTPS ? "smtps" : "smtp";
        int port = mailUseSMTPS ? 465 : 25;
        Transport transport = session.getTransport(protocol);
        logger.debug("Sending e-mail to {} using {}@{}:{}", msg.getAllRecipients(), mailUser, mailHost, port);
        transport.connect(mailHost, port, mailUser, mailPassword);
        msg.saveChanges();
        transport.sendMessage(msg, msg.getAllRecipients());
        transport.close();
        logger.debug("Message sent successfully");
    }

    /**
     * Convenience method for creating an e-mail message from a set of message properties.
     *
     * @param session the mail Session instance to use
     * @param properties the message properties
     *
     * @return a Message instance
     *
     * @since hobson-hub-api 0.1.6
     */
    protected Message createMessage(Session session, Map<String,Object> properties) {
        String recipientAddress = (String) properties.get(RECIPIENT_ADDRESS);
        String subject = (String) properties.get(SUBJECT);
        String message = (String) properties.get(MESSAGE);

        if (config.getSenderAddress() == null) {
            throw new HobsonRuntimeException("No sender address specified; unable to execute e-mail action");
        } else if (recipientAddress == null) {
            throw new HobsonRuntimeException("No recipient address specified; unable to execute e-mail action");
        } else if (subject == null) {
            throw new HobsonRuntimeException("No subject specified; unable to execute e-mail action");
        } else if (message == null) {
            throw new HobsonRuntimeException("No message body specified; unable to execute e-mail action");
        }

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(config.getSenderAddress()));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientAddress));
            msg.setSubject(subject);
            msg.setText(message);

            return msg;
        } catch (MessagingException e) {
            throw new HobsonRuntimeException("Unable to create mail message", e);
        }
    }
}
