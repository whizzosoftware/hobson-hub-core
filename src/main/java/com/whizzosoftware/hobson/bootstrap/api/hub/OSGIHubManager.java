/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
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
import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.event.hub.HubConfigurationUpdateEvent;
import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.data.DataStreamManager;
import com.whizzosoftware.hobson.api.variable.GlobalVariable;
import com.whizzosoftware.hobson.api.variable.GlobalVariableContext;
import com.whizzosoftware.hobson.api.variable.GlobalVariableDescriptor;
import gnu.io.CommPortIdentifier;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.osgi.framework.*;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * An OSGi implementation of HubManager.
 *
 * @author Dan Noguerol
 */
public class OSGIHubManager implements HubManager, LocalHubManager {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OSGIHubManager.class);

    private static final String HOBSON_LOGGER = "com.whizzosoftware.hobson";
    private static final String LOG_LEVEL = "logLevel";

    volatile private BundleContext bundleContext;
    volatile private ConfigurationManager configManager;
    volatile private EventManager eventManager;

    private PropertyContainerClass localHubConfigClass;
    private NetworkInfo networkInfo;
    private Map<String,ServiceRegistration> webAppMap = Collections.synchronizedMap(new HashMap<String,ServiceRegistration>());
    private Map<GlobalVariableContext,Object> globalVariableMap = new HashMap<>();
    private WebSocketInfo webSocketInfo;

    public void start() {
        // set the log level
        String logLevel = configManager != null ? (String)configManager.getHubConfigurationProperty(HubContext.createLocal(), LOG_LEVEL) : null;
        if (logLevel != null) {
            ((Logger) LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.toLevel(logLevel));
        }

        localHubConfigClass = new HubConfigurationClass();
    }

    @Override
    public String getVersion(HubContext hubContext) {
        for (Bundle b : bundleContext.getBundles()) {
            if ("com.whizzosoftware.hobson.hub.hobson-hub-core".equals(b.getSymbolicName())) {
                return b.getVersion().toString();
            }
        }
        return null;
    }

    @Override
    public Collection<HubContext> getHubs() {
        return Collections.singletonList(HubContext.createLocal());
    }

    public Collection<HubContext> getHubs(String userId) {
        return getHubs();
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
    public String getUserIdForHubId(String hubId) {
        return HubContext.DEFAULT_USER;
    }

    @Override
    public void deleteConfiguration(HubContext ctx) {
        configManager.deleteHubConfiguration(ctx);
        eventManager.postEvent(ctx, new HubConfigurationUpdateEvent(System.currentTimeMillis(), getConfiguration(ctx).getPropertyValues()));
    }

    @Override
    public boolean authenticateHub(HubCredentials credentials) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyContainer getConfiguration(HubContext ctx) {
        if (HubContext.DEFAULT_HUB.equals(ctx.getHubId())) {
            Map<String, Object> values = new HashMap<>();
            if (configManager != null) {
                values.putAll(configManager.getHubConfiguration(ctx));
            }
            values.put(LOG_LEVEL, ((Logger) LoggerFactory.getLogger(HOBSON_LOGGER)).getLevel().toString());
            if (System.getProperty("useSSL") != null) {
                values.put(HubConfigurationClass.SSL_MODE, true);
            }
            if (!values.containsKey(HubConfigurationClass.AWAY)) {
                values.put(HubConfigurationClass.AWAY, false);
            }
            return new PropertyContainer(getConfigurationClass(ctx).getContext(), values);
        } else {
            return null;
        }
    }

    @Override
    public PropertyContainerClass getConfigurationClass(HubContext ctx) {
        if (HubContext.DEFAULT_HUB.equals(ctx.getHubId())) {
            return localHubConfigClass;
        } else {
            return null;
        }
    }

    @Override
    public PropertyContainerClass getContainerClass(PropertyContainerClassContext ctx) {
        if (ctx != null) {
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
        } else {
            throw new HobsonRuntimeException("Unable to obtain property container class for null context");
        }
    }

    @Override
    public boolean hasPropertyContainerClass(PropertyContainerClassContext ctx) {
        if (ctx != null) {
            try {
                Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + "))");
                ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
                return (refs != null && refs.length == 1);
            } catch (InvalidSyntaxException e) {
                logger.error("Error retrieving container class: " + ctx, e);
            }
        }
        return false;
    }

    private String getHubName(HubContext ctx) {
        String name = null;
        if (configManager != null) {
            Map<String, Object> props = configManager.getHubConfiguration(ctx);
            if (props != null) {
                name = (String) props.get("name");
            }
        }
        return (name == null) ? "Unnamed" : name;
    }

    @Override
    public NetworkInfo getNetworkInfo() throws IOException {
        if (networkInfo == null) {
            String nicString = System.getProperty("force.nic");
            NetworkInterface nic;
            InetAddress addr = null;
            if (nicString != null) {
                addr = InetAddress.getByName(nicString);
                nic = NetworkInterface.getByInetAddress(addr);
            } else {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                for (NetworkInterface iface : Collections.list(interfaces)) {
                    if (!iface.isLoopback() && iface.isUp()) {
                        Enumeration<InetAddress> addresses = iface.getInetAddresses();
                        for (InetAddress candidate : Collections.list(addresses)) {
                            if (!(candidate instanceof Inet6Address) && candidate.isReachable(3000)) {
                                logger.trace("Checking network interface with address {}", candidate);
                                try (SocketChannel socket = SocketChannel.open()) {
                                    socket.socket().setSoTimeout(3000);
                                    socket.bind(new InetSocketAddress(candidate, 8080));
                                    socket.connect(new InetSocketAddress("google.com", 80));
                                    addr = candidate;
                                    break;
                                } catch (IOException ignored) {
                                }
                            }
                        }
                        if (addr != null) {
                            break;
                        }
                    }
                }
                if (addr == null) {
                    logger.error("Unable to find suitable network interface; defaulting to localhost");
                    addr = InetAddress.getLocalHost();
                }
                nic = NetworkInterface.getByInetAddress(addr);
            }
            networkInfo = new NetworkInfo(nic, addr);
            logger.info("Using network interface with address: " + addr.getHostAddress());
        }

        return networkInfo;
    }

    @Override
    public void sendTestEmail(HubContext ctx, PropertyContainer config) {
        sendEmail(config, config.getStringPropertyValue(HubConfigurationClass.EMAIL_SENDER), "Hobson Test Message", "This is a test message from Hobson. If you're reading this, your e-mail configuration is working!");
    }

    @Override
    public void sendEmail(HubContext ctx, String recipientAddress, String subject, String body) {
        sendEmail(getConfiguration(ctx), recipientAddress, subject, body);
    }

    @Override
    public void setConfiguration(HubContext ctx, PropertyContainer configuration) {
        PropertyContainer pc = getConfiguration(ctx);

        // add existing configuration values if necessary
        for (String key : pc.getPropertyValues().keySet()) {
            if (!configuration.hasPropertyValue(key)) {
                configuration.setPropertyValue(key, pc.getPropertyValue(key));
            }
        }

        // set log level if specified
        if (configuration.hasPropertyValue(LOG_LEVEL)) {
            ((Logger)LoggerFactory.getLogger(HOBSON_LOGGER)).setLevel(Level.toLevel((String)configuration.getPropertyValue(LOG_LEVEL)));
        }

        updateConfiguration(ctx, configuration.getPropertyValues());
    }

    @Override
    public void setGlobalVariable(GlobalVariableContext gctx, Object value, long timestamp) {
        globalVariableMap.put(gctx, value);
    }

    @Override
    public void setGlobalVariables(Map<GlobalVariableContext, Object> values, long timestamp) {
        for (GlobalVariableContext gvctx : values.keySet()) {
            setGlobalVariable(gvctx, values.get(gvctx), timestamp);
        }
    }

    @Override
    public GlobalVariable getGlobalVariable(GlobalVariableContext gvctx) {
        Object value = globalVariableMap.get(gvctx);
        return new GlobalVariable(new GlobalVariableDescriptor(gvctx), null, value);
    }

    @Override
    public Collection<GlobalVariable> getGlobalVariables(HubContext hctx) {
        List<GlobalVariable> results = new ArrayList<>();
        for (GlobalVariableContext gvctx : globalVariableMap.keySet()) {
            Object value = globalVariableMap.get(gvctx);
            results.add(new GlobalVariable(new GlobalVariableDescriptor(gvctx), null, value));
        }
        return results;
    }

    @Override
    public void addDataStreamManager(DataStreamManager dataStreamManager) {
        // register data stream manager
        bundleContext.registerService(DataStreamManager.class.getName(), dataStreamManager, null);
    }

    @Override
    public void publishWebApplication(HubWebApplication app) {
        webAppMap.put(app.getPath(), bundleContext.registerService(HubWebApplication.class.getName(), app, null));
    }

    @Override
    public void unpublishWebApplication(String path) {
        ServiceRegistration sr = webAppMap.get(path);
        if (sr != null) {
            sr.unregister();
            webAppMap.remove(path);
        }
    }

    private void sendEmail(PropertyContainer config, String recipientAddress, String subject, String body) {
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

    private HobsonHub createLocalHubDetails() {
        Bundle b = FrameworkUtil.getBundle(getClass());
        String version = b != null ? b.getVersion().toString() : null;
        HubContext ctx = HubContext.createLocal();
        return new HobsonHub.Builder(ctx).
            name(getHubName(ctx)).
            version(version).
            configurationClass(localHubConfigClass).
            configuration(getConfiguration(HubContext.createLocal()).getPropertyValues()).
            webSocketInfo(webSocketInfo).
            build();
    }

    @Override
    public LineRange getLog(HubContext ctx, long startLine, long endLine, Appendable appendable) {
        return getLog(ctx, getLogFilePath(), startLine, endLine, appendable);
    }

    protected LineRange getLog(HubContext ctx, String path, long startLine, long endLine, Appendable appendable) {
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
                if (s.charAt(0) != '{') {
                    s = "{\"message\":\"" + s + "\"}";
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
    public Collection<String> getSerialPorts(HubContext hctx) {
        List<String> results = new ArrayList<>();
        Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
        while (ports.hasMoreElements()) {
            CommPortIdentifier cpi = ports.nextElement();
            if (cpi.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                results.add(cpi.getName());
            }
        }
        return results;
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

    @Override
    public void setWebSocketInfo(String protocol, int port, String path) {
        this.webSocketInfo = new WebSocketInfo(protocol, port, path);
    }

    private String getLogFilePath() {
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

    private void updateConfiguration(HubContext ctx, Map<String,Object> config) {
        try {
            configManager.setHubConfiguration(ctx, config);
            eventManager.postEvent(ctx, new HubConfigurationUpdateEvent(System.currentTimeMillis(), config));
        } catch (NotSerializableException e) {
            throw new HobsonRuntimeException("Unable to update hub configuration: " + config, e);
        }
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
    Message createMessage(Session session, PropertyContainer config, String recipientAddress, String subject, String message) {
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
