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
import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.config.EmailConfiguration;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.osgi.framework.FrameworkUtil;
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
public class OSGIHubManager implements HubManager, LocalHubManager {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OSGIHubManager.class);

    public static final String HUB_NAME = "hub.name";
    public static final String ADMIN_PASSWORD = "admin.password";
    public static final String SETUP_COMPLETE = "setup.complete";
    public static final String HOBSON_LOGGER = "com.whizzosoftware.hobson";

    volatile private ConfigurationAdmin configAdmin;
    volatile private EventManager eventManager;

    public Collection<HobsonHub> getHubs(String userId) {
        return Arrays.asList(createLocalHubDetails(userId));
    }

    @Override
    public HobsonHub getHub(HubContext ctx) {
        if (HubContext.DEFAULT_HUB.equals(ctx.getHubId())) {
            return createLocalHubDetails(ctx.getUserId());
        } else {
            throw new HobsonNotFoundException("Unable to find hub with specified ID");
        }
    }

    @Override
    public void setHubDetails(HobsonHub hub) {
        boolean needsUpdate = false;
        Configuration config = getConfiguration();
        Dictionary props = getConfigurationProperties(config);

        if (hub.hasName()) {
            props.put(HUB_NAME, hub.getName());
            needsUpdate = true;
        }

        if (hub.hasLocation()) {
            HubLocation location = hub.getLocation();
            if (location.getText() != null) {
                props.put(HubLocation.PROP_LOCATION_STRING, location.getText());
                needsUpdate = true;
            }
            if (location.hasLatitude()) {
                props.put(HubLocation.PROP_LATITUDE, location.getLatitude());
                needsUpdate = true;
            }
            if (location.hasLongitude()) {
                props.put(HubLocation.PROP_LONGITUDE, location.getLongitude());
                needsUpdate = true;
            }
        }

        if (hub.hasEmail()) {
            EmailConfiguration email = hub.getEmail();
            props.put(EmailConfiguration.PROP_MAIL_SERVER, email.getServer());
            props.put(EmailConfiguration.PROP_MAIL_SECURE, email.isSecure());
            props.put(EmailConfiguration.PROP_MAIL_USERNAME, email.getUsername());
            if (email.hasPassword()) {
                props.put(EmailConfiguration.PROP_MAIL_PASSWORD, email.getPassword());
            }
            props.put(EmailConfiguration.PROP_MAIL_SENDER, email.getSenderAddress());
            needsUpdate = true;
        }

        if (hub.hasLogLevel()) {
            Logger root = (Logger)LoggerFactory.getLogger(HOBSON_LOGGER);
            root.setLevel(Level.toLevel(hub.getLogLevel()));
        }

        if (hub.hasSetupComplete()) {
            props.put(SETUP_COMPLETE, hub.isSetupComplete());
            needsUpdate = true;
        }

        if (needsUpdate) {
            try {
                updateConfiguration(hub.getContext(), config, props);
            } catch (IOException e) {
                throw new HobsonRuntimeException("Error setting hub details", e);
            }
        }
    }

    @Override
    public void clearHubDetails(HubContext ctx) {
        Configuration config = getConfiguration();
        Dictionary props = new Hashtable();
        try {
            updateConfiguration(ctx, config, props);
            ((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.INFO);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error clearing hub details", e);
        }
    }

    protected String getHubName(HubContext ctx) {
        Configuration config = getConfiguration();
        Dictionary props = getConfigurationProperties(config);
        String name = (String)props.get(HUB_NAME);
        return (name == null) ? "Unnamed" : name;
    }

    @Override
    public void setHubPassword(HubContext ctx, PasswordChange change) {
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

            updateConfiguration(ctx, config, props);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub password", e);
        }
    }

    @Override
    public boolean authenticateAdmin(HubContext ctx, String password) {
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
            adminPassword = DigestUtils.sha256Hex("local");
        }

        return (adminPassword.equals(password));
    }

    protected HubLocation getHubLocation(HubContext ctx) {
        return new HubLocation.Builder().dictionary(getConfigurationProperties(getConfiguration())).build();
    }

    protected EmailConfiguration getHubEmailConfiguration(HubContext ctx) {
        return new EmailConfiguration.Builder().dictionary(getConfigurationProperties(getConfiguration())).build();
    }

    @Override
    public void sendTestEmail(HubContext ctx, EmailConfiguration config) {
        sendEmail(config, config.getSenderAddress(), "Hobson Test Message", "This is a test message from Hobson. If you're reading this, your e-mail configuration is working!");
    }

    @Override
    public void sendEmail(HubContext ctx, String recipientAddress, String subject, String body) {
        sendEmail(getHubEmailConfiguration(ctx), recipientAddress, subject, body);
    }

    protected void sendEmail(EmailConfiguration config, String recipientAddress, String subject, String body) {
        String mailHost = config.getServer();
        Boolean mailSecure = config.isSecure();
        String mailUser = config.getUsername();
        String mailPassword = config.getPassword();

        if (mailHost == null) {
            throw new HobsonRuntimeException("No mail host is configured; unable to execute e-mail action");
        } else if (mailSecure && mailUser == null) {
            throw new HobsonRuntimeException("No mail server username is configured for SMTPS; unable to execute e-mail action");
        } else if (mailSecure && mailPassword == null) {
            throw new HobsonRuntimeException("No mail server password is configured for SMTPS; unable to execute e-mail action");
        }

        // create mail session
        Properties props = new Properties();
        props.put("mail.smtp.host", mailHost);
        Session session = Session.getDefaultInstance(props, null);

        // create the mail message
        Message msg = createMessage(session, config, recipientAddress, subject, body);

        // send the message
        String protocol = mailSecure ? "smtps" : "smtp";
        int port = mailSecure ? 465 : 25;
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

    protected HobsonHub createLocalHubDetails(String userId) {
        String version = FrameworkUtil.getBundle(getClass()).getVersion().toString();
        HubContext ctx = HubContext.createLocal();
        return new HobsonHub.Builder(ctx).
            name(getHubName(ctx)).
            version(version).
            email(getHubEmailConfiguration(ctx)).
            location(getHubLocation(ctx)).
            logLevel(((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).getLevel().toString()).
            setupComplete(isSetupComplete()).
            build();
    }

    protected boolean isSetupComplete() {
        Configuration config = getConfiguration();
        Dictionary props = config.getProperties();
        return (props != null && props.get(SETUP_COMPLETE) != null && (Boolean)props.get(SETUP_COMPLETE));
    }

    @Override
    public LineRange getLog(HubContext ctx, long startLine, long endLine, Appendable appendable) {
        String path = getLogFilePath();

        try {
            ReversedLinesFileReader reader = new ReversedLinesFileReader(new File(path));

            long lineCount = endLine - startLine + 1;

            // read to the start line
            for (int i=0; i < startLine; i++) {
                reader.readLine();
            }

            // append the requested number of lines (aborting if start of file is hit)
            appendable.append("[\n");
            long count = 0;
            for (; count < lineCount; count++) {
                String s = reader.readLine();
                if (s == null) {
                    break;
                }
                appendable.append(s);
                if (count < lineCount - 1) {
                    appendable.append(",");
                }
            }
            appendable.append("\n]");

            return new LineRange(startLine, count - 1);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to read log file", e);
        }
    }

    @Override
    public HubRegistrar getRegistrar() {
        return null;
    }

    @Override
    public LocalHubManager getLocalManager() {
        return this;
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

    private void updateConfiguration(HubContext ctx, Configuration config, Dictionary props) throws IOException {
        config.update(props);
        eventManager.postEvent(ctx, new HubConfigurationUpdateEvent());
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
