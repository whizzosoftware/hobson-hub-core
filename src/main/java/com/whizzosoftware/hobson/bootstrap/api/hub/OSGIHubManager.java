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
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.osgi.framework.*;
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

    public static final String ADMIN_PASSWORD = "adminPassword";
    public static final String HOBSON_LOGGER = "com.whizzosoftware.hobson";
    public static final String LOG_LEVEL = "logLevel";

    volatile private BundleContext bundleContext;
    volatile private ConfigurationAdmin configAdmin;
    volatile private EventManager eventManager;
    volatile private PluginManager pluginManager;

    public void start() {
        String logLevel = (String)getConfigurationProperty(LOG_LEVEL);
        if (logLevel != null) {
            ((Logger) LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.toLevel(logLevel));
        }
    }

    public Collection<HobsonHub> getHubs(String userId) {
        return Arrays.asList(createLocalHubDetails());
    }

    @Override
    public HobsonHub getHub(HubContext ctx) {
        if (HubContext.DEFAULT_HUB.equals(ctx.getHubId())) {
            return createLocalHubDetails();
        } else {
            throw new HobsonNotFoundException("Unable to find hub with specified ID");
        }
    }

    @Override
    public HobsonHub addHub(String userId, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteConfiguration(HubContext ctx) {
        Configuration config = getConfiguration();
        Dictionary props = new Hashtable();
        try {
            updateConfiguration(ctx, config, props);
            ((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.INFO);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error clearing hub details", e);
        }
    }

    @Override
    public void removeHub(HubContext ctx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean authenticateHub(HubContext ctx, HubCredentials credentials) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyContainer getConfiguration(HubContext ctx) {
        PropertyContainer pc = new PropertyContainer(
            PropertyContainerClassContext.create(ctx, "hubConfiguration"),
            getConfigurationPropertyMap(getConfiguration())
        );
        pc.setPropertyValue(LOG_LEVEL, ((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).getLevel().toString());

        return pc;
    }

    @Override
    public PropertyContainerClass getConfigurationClass(HubContext ctx) {
        return getHub(ctx).getConfigurationClass();
    }

    @Override
    public PropertyContainerClass getContainerClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (PropertyContainerClass)bundleContext.getService(refs[0]);
            } else {
                throw new HobsonRuntimeException("Unable to find container class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving container class: " + ctx, e);
        }
    }

    protected String getHubName(HubContext ctx) {
        Configuration config = getConfiguration();
        Dictionary props = getConfigurationProperties(config);
        String name = (String)props.get("name");
        return (name == null) ? "Unnamed" : name;
    }

    @Override
    public void setLocalPassword(HubContext ctx, PasswordChange change) {
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
    public boolean authenticateLocal(HubContext ctx, String password) {
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

        return (adminPassword.equals(DigestUtils.sha256Hex(password)));
    }

    protected PropertyContainer getHubEmailConfiguration(HubContext ctx) {
        return new PropertyContainer(null, getConfigurationPropertyMap(getConfiguration()));
    }

    @Override
    public void sendTestEmail(HubContext ctx, PropertyContainer config) {
        sendEmail(config, config.getStringPropertyValue(HubConfigurationClass.EMAIL_SENDER), "Hobson Test Message", "This is a test message from Hobson. If you're reading this, your e-mail configuration is working!");
    }

    @Override
    public void sendEmail(HubContext ctx, String recipientAddress, String subject, String body) {
        sendEmail(getHubEmailConfiguration(ctx), recipientAddress, subject, body);
    }

    @Override
    public void setConfiguration(HubContext ctx, PropertyContainer configuration) {
        Configuration config = getConfiguration();
        Dictionary props = getConfigurationProperties(config);

        // set properties
        for (String name : configuration.getPropertyValues().keySet()) {
            props.put(name, configuration.getPropertyValue(name));
            if (name.equals(LOG_LEVEL)) {
                ((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.toLevel((String)configuration.getPropertyValue(name)));
            }
        }

        try {
            updateConfiguration(ctx, config, props);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Error setting hub configuration", e);
        }
    }

    protected void sendEmail(PropertyContainer config, String recipientAddress, String subject, String body) {
        String mailHost = config.getStringPropertyValue(HubConfigurationClass.EMAIL_SERVER);
        Boolean mailSecure = config.getBooleanPropertyValue(HubConfigurationClass.EMAIL_SECURE);
        String mailUser = config.getStringPropertyValue(HubConfigurationClass.EMAIL_USER);
        String mailPassword = config.getStringPropertyValue(HubConfigurationClass.EMAIL_PASSWORD);

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

    protected HobsonHub createLocalHubDetails() {
        String version = FrameworkUtil.getBundle(getClass()).getVersion().toString();
        HubContext ctx = HubContext.createLocal();
        return new HobsonHub.Builder(ctx).
            name(getHubName(ctx)).
            version(version).
            configuration(getConfigurationPropertyMap(getConfiguration())).
            build();
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
                } else if (count > 0) {
                    appendable.append(",");
                }
                appendable.append("{\"item\":").append(s).append("}");
            }
            appendable.append("\n]");

            return new LineRange(startLine, count - 1);
        } catch (IOException e) {
            throw new HobsonRuntimeException("Unable to read log file", e);
        }
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
        if (configAdmin != null) {
            try {
                return configAdmin.getConfiguration("com.whizzosoftware.hobson.hub");
            } catch (IOException e) {
                throw new HobsonRuntimeException("Unable to retrieve hub configuration", e);
            }
        } else {
            return null;
        }
    }

    private Object getConfigurationProperty(String name) {
        Configuration config = getConfiguration();
        if (config != null) {
            Dictionary d = config.getProperties();
            if (d != null) {
                return d.get(name);
            }
        }
        return null;
    }

    private Dictionary getConfigurationProperties(Configuration config) {
        Dictionary p = config.getProperties();
        if (p == null) {
            p = new Properties();
        }
        return p;
    }

    private Map<String,Object> getConfigurationPropertyMap(Configuration config) {
        Dictionary p = config.getProperties();
        Map<String,Object> pm = new HashMap<>();
        if (p != null) {
            Enumeration e = p.keys();
            while (e.hasMoreElements()) {
                Object k = e.nextElement();
                pm.put(k.toString(), p.get(k));
            }
        }
        return pm;
    }

    private void updateConfiguration(HubContext ctx, Configuration config, Dictionary props) throws IOException {
        config.update(props);
        eventManager.postEvent(ctx, new HubConfigurationUpdateEvent(System.currentTimeMillis()));
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
    protected Message createMessage(Session session, PropertyContainer config, String recipientAddress, String subject, String message) {
        if (!config.hasPropertyValue(HubConfigurationClass.EMAIL_SENDER)) {
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
            msg.setFrom(new InternetAddress(config.getStringPropertyValue(HubConfigurationClass.EMAIL_SENDER)));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientAddress));
            msg.setSubject(subject);
            msg.setText(message);

            return msg;
        } catch (MessagingException e) {
            throw new HobsonInvalidRequestException("Unable to create mail message", e);
        }
    }
}
