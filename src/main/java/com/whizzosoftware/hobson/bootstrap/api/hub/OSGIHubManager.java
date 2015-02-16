/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.hub;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.spi.FilterReply;
import com.whizzosoftware.hobson.api.HobsonInvalidRequestException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.util.UserUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.util.*;

/**
 * An OSGi implementation of HubManager.
 *
 * @author Dan Noguerol
 */
public class OSGIHubManager implements HubManager {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OSGIHubManager.class);

    public static final String HUB_NAME = "hub.name";
    public static final String ADMIN_PASSWORD = "admin.password";
    public static final String SETUP_COMPLETE = "setup.complete";
    public static final String HOBSON_LOGGER = "com.whizzosoftware.hobson";
    public static final String RECIPIENT_ADDRESS = "recipientAddress";
    public static final String SUBJECT = "subject";
    public static final String MESSAGE = "message";

    volatile private ConfigurationAdmin configAdmin;
    volatile private EventManager eventManager;

    @Override
    public String getHubName(String userId, String hubId) {
        Configuration config = getConfiguration();
        Dictionary props = getConfigurationProperties(config);
        return (String)props.get(HUB_NAME);
    }

    @Override
    public void setHubName(String userId, String hubId, String name) {
        try {
            Configuration config = getConfiguration();
            Dictionary props = getConfigurationProperties(config);
            props.put(HUB_NAME, name);

            updateConfiguration(config, props);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub name", e);
        }
    }

    @Override
    public void setHubPassword(String userId, String hubId, PasswordChange change) {
        try {
            Configuration config = getConfiguration();
            Dictionary props = getConfigurationProperties(config);

            // verify the old password
            String shaOld = DigestUtils.sha256Hex(change.getCurrentPassword());
            String hubPassword = (String)props.get(ADMIN_PASSWORD);
            if (hubPassword != null && !hubPassword.equals(shaOld)) {
                throw new HobsonRuntimeException("The current hub password is invalid");
            }

            // verify the password meets complexity requirements
            if (!change.isValid()) {
                throw new HobsonInvalidRequestException("New password does not meet complexity requirements");
            }

            // set the new password
            props.put(ADMIN_PASSWORD, DigestUtils.sha256Hex(change.getNewPassword()));

            updateConfiguration(config, props);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub password", e);
        }
    }

    @Override
    public boolean authenticateAdmin(String userId, String hubId, String password) {
        String adminPassword = null;
        Configuration config = getConfiguration();

        // if there's configuration available, try to obtain the encrypted admin password
        if (config != null) {
            Dictionary d = config.getProperties();
            if (d != null) {
                adminPassword = (String)d.get(ADMIN_PASSWORD);
            }
        }

        // if it hasn't been set, default to the "admin" password
        if (adminPassword == null) {
            adminPassword = DigestUtils.sha256Hex("admin");
        }

        return (adminPassword.equals(password));
    }

    @Override
    public HubLocation getHubLocation(String userId, String hubId) {
        return new HubLocation(getConfigurationProperties(getConfiguration()));
    }

    @Override
    public void setHubLocation(String userId, String hubId, HubLocation location) {
        try {
            Configuration config = getConfiguration();
            Dictionary d = getConfigurationProperties(config);
            if (location.getText() != null) {
                d.put(HubLocation.PROP_LOCATION_STRING, location.getText());
            }
            if (location.hasLatitude()) {
                d.put(HubLocation.PROP_LATITUDE, location.getLatitude());
            }
            if (location.hasLongitude()) {
                d.put(HubLocation.PROP_LONGITUDE, location.getLongitude());
            }

            updateConfiguration(config, d);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub location", e);
        }
    }

    @Override
    public EmailConfiguration getHubEmailConfiguration(String userId, String hubId) {
        return new EmailConfiguration(getConfigurationProperties(getConfiguration()));
    }

    @Override
    public void setHubEmailConfiguration(String userId, String hubId, EmailConfiguration ec) {
        try {
            Configuration config = getConfiguration();
            Dictionary d = getConfigurationProperties(config);
            d.put(EmailConfiguration.PROP_MAIL_SERVER, ec.getMailServer());
            d.put(EmailConfiguration.PROP_MAIL_SMTPS, ec.isSMTPS());
            d.put(EmailConfiguration.PROP_MAIL_USERNAME, ec.getUsername());
            d.put(EmailConfiguration.PROP_MAIL_PASSWORD, ec.getPassword());
            d.put(EmailConfiguration.PROP_MAIL_SENDER, ec.getSenderAddress());

            updateConfiguration(config, d);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub location", e);
        }
    }

    @Override
    public void sendTestEmail(String userId, String hubId, EmailConfiguration config) {
        sendEmail(config, config.getSenderAddress(), "Hobson Test Message", "This is a test message from Hobson. If you're reading this, your e-mail configuration is working!");
    }

    @Override
    public void sendEmail(String userId, String hubId, String recipientAddress, String subject, String body) {
        sendEmail(getHubEmailConfiguration(userId, hubId), recipientAddress, subject, body);
    }

    protected void sendEmail(EmailConfiguration config, String recipientAddress, String subject, String body) {
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
        Message msg = createMessage(session, config, recipientAddress, subject, body);

        // send the message
        String protocol = mailUseSMTPS ? "smtps" : "smtp";
        int port = mailUseSMTPS ? 465 : 25;
        try {
            Transport transport = session.getTransport(protocol);
            logger.debug("Sending e-mail to {} using {}@{}:{}", msg.getAllRecipients(), mailUser, mailHost, port);
            transport.connect(mailHost, port, mailUser, mailPassword);
            msg.saveChanges();
            transport.sendMessage(msg, msg.getAllRecipients());
            transport.close();
            logger.debug("Message sent successfully");
        } catch (MessagingException e) {
            logger.error("Error sending e-mail message", e);
            throw new HobsonRuntimeException("Error sending e-mail message", e);
        }
    }

    @Override
    public boolean isSetupWizardComplete(String userId, String hubId) {
        Configuration config = getConfiguration();
        Dictionary props = config.getProperties();
        return (props != null && props.get(SETUP_COMPLETE) != null && (Boolean)props.get(SETUP_COMPLETE));
    }

    @Override
    public void setSetupWizardComplete(String userId, String hubId, boolean complete) {
        try {
            Configuration config = getConfiguration();
            Dictionary props = config.getProperties();
            if (props == null) {
                props = new Hashtable();
            }
            props.put(SETUP_COMPLETE, complete);

            updateConfiguration(config, props);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting setup wizard completion status", e);
        }
    }

    @Override
    public String getLogLevel(String userId, String hubId) {
        Logger root = (Logger) LoggerFactory.getLogger(HOBSON_LOGGER);
        return root.getLevel().toString();
    }

    @Override
    public void setLogLevel(String userId, String hubId, String level) {
        Logger root = (Logger) LoggerFactory.getLogger(HOBSON_LOGGER);
        root.setLevel(Level.toLevel(level));
    }

    @Override
    public LogContent getLog(String userId, String hubId, long startIndex, long endIndex) {
        String path = getLogFilePath();

        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long fileLength = file.length();

            if (startIndex == -1) {
                startIndex = fileLength - endIndex - 1;
                endIndex = fileLength - 1;
            }
            if (endIndex == -1) {
                endIndex = fileLength - 1;
            }
            if (startIndex > fileLength - 1) {
                throw new HobsonRuntimeException("Requested start index is greater than file length");
            }
            if (endIndex > fileLength - 1) {
                throw new HobsonRuntimeException("Requested end index is greater than file length");
            }

            // jump to start point in file
            file.seek(startIndex);

            // read in appropriate number of bytes
            byte buffer[] = new byte[(int)(endIndex - startIndex)];
            file.read(buffer);

            return new LogContent(startIndex, endIndex, buffer);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to read log file", e);
        }
    }

    @Override
    public void addErrorLogAppender(Object aAppender) {
        Appender appender = (Appender)aAppender;

        LevelFilter filter = new LevelFilter();
        filter.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        filter.setLevel(Level.ERROR);
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();
        appender.addFilter(filter);

        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();

        ((Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
    }

    @Override
    public void removeLogAppender(Object aAppender) {
        Appender appender = (Appender)aAppender;
        Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(appender);
    }

    protected String getLogFilePath() {
        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        for (Logger logger : context.getLoggerList()) {
            for (Iterator<Appender<ILoggingEvent>> index = logger.iteratorForAppenders(); index.hasNext();) {
                Appender<ILoggingEvent> appender = index.next();
                if ("FILE".equals(appender.getName()) && appender instanceof FileAppender) {
                    return ((FileAppender)appender).getFile();
                }
            }
        }
        return null;
    }

    private Configuration getConfiguration() {
        try {
            return configAdmin.getConfiguration("com.whizzosoftware.hobson.hub");
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to retrieve hub configuration", e);
        }
    }

    private Dictionary getConfigurationProperties(Configuration config) {
        Dictionary p = config.getProperties();
        if (p == null) {
            p = new Properties();
        }
        return p;
    }

    private void updateConfiguration(Configuration config, Dictionary props) throws IOException {
        config.update(props);
        eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new HubConfigurationUpdateEvent());
    }

    /**
     * Convenience method for creating an e-mail message from a set of message properties.
     *
     * @param session the mail Session instance to use
     * @param config the email configuration to use
     * @param recipientAddress the e-mail address of the recipient
     * @param subject the e-mail subject line
     * @param message the e-mail message body
     *
     * @return a Message instance
     *
     * @since hobson-hub-api 0.1.6
     */
    protected Message createMessage(Session session, EmailConfiguration config, String recipientAddress, String subject, String message) {
        if (config.getSenderAddress() == null) {
            throw new HobsonInvalidRequestException("No sender address specified; unable to execute e-mail action");
        } else if (recipientAddress == null) {
            throw new HobsonInvalidRequestException("No recipient address specified; unable to execute e-mail action");
        } else if (subject == null) {
            throw new HobsonInvalidRequestException("No subject specified; unable to execute e-mail action");
        } else if (message == null) {
            throw new HobsonInvalidRequestException("No message body specified; unable to execute e-mail action");
        }

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(config.getSenderAddress()));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientAddress));
            msg.setSubject(subject);
            msg.setText(message);

            return msg;
        } catch (MessagingException e) {
            throw new HobsonInvalidRequestException("Unable to create mail message", e);
        }
    }
}
