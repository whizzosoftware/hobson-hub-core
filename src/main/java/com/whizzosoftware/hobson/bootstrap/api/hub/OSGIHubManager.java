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
import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.image.ImageInputStream;
import com.whizzosoftware.hobson.api.image.ImageMediaTypes;
import com.whizzosoftware.hobson.api.util.UserUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

/**
 * An OSGi implementation of HubManager.
 *
 * @author Dan Noguerol
 */
public class OSGIHubManager implements HubManager {
    public static final String HUB_NAME = "hub.name";
    public static final String ADMIN_PASSWORD = "admin.password";
    public static final String SETUP_COMPLETE = "setup.complete";
    public static final String HOBSON_LOGGER = "com.whizzosoftware.hobson";

    private static final String HUB_JPEG = "hub.jpg";
    private static final String HUB_PNG = "hub.png";

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
            d.put(HubLocation.PROP_LOCATION_STRING, location.getText());
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
    public ImageInputStream getHubImage() {
        BundleContext bc = FrameworkUtil.getBundle(getClass()).getBundleContext();

        // look for a hub image
        File file = bc.getDataFile(HUB_JPEG);
        String mediaType = ImageMediaTypes.JPEG;
        if (!file.exists()) {
            file = bc.getDataFile(HUB_PNG);
            mediaType = ImageMediaTypes.PNG;
            if (!file.exists()) {
                throw new HobsonNotFoundException("Hub image not found");
            }
        }

        try {
            return new ImageInputStream(mediaType, new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new HobsonNotFoundException("No hub image found", e);
        }
    }

    @Override
    public void setHubImage(ImageInputStream iis) {
        String filename = null;

        if (iis.getMediaType().equalsIgnoreCase(ImageMediaTypes.JPEG)) {
            filename = HUB_JPEG;
        } else if (iis.getMediaType().equalsIgnoreCase(ImageMediaTypes.PNG)) {
            filename = HUB_PNG;
        }

        if (filename != null) {
            try {
                FileUtils.copyInputStreamToFile(
                    iis.getInputStream(),
                    FrameworkUtil.getBundle(getClass()).getBundleContext().getDataFile(filename)
                );
            } catch (IOException e) {
                throw new HobsonRuntimeException("Error writing hub image", e);
            }
        } else {
            throw new HobsonRuntimeException("Unsupported image media type");
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

            if (fileLength > endIndex) {
                endIndex = fileLength - 1;
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
}
