/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap;

import com.google.inject.Guice;
import com.whizzosoftware.hobson.api.activity.ActivityLogManager;
import com.whizzosoftware.hobson.api.config.ConfigurationManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubConfigurationClass;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.image.ImageManager;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.activity.OSGIActivityLogManager;
import com.whizzosoftware.hobson.bootstrap.api.config.MapDBConfigurationManager;
import com.whizzosoftware.hobson.bootstrap.api.device.OSGIDeviceManager;
import com.whizzosoftware.hobson.bootstrap.api.disco.OSGIDiscoManager;
import com.whizzosoftware.hobson.bootstrap.api.event.OSGIEventManager;
import com.whizzosoftware.hobson.bootstrap.api.hub.OSGIHubManager;
import com.whizzosoftware.hobson.bootstrap.api.image.OSGIImageManager;
import com.whizzosoftware.hobson.bootstrap.api.plugin.OSGIPluginManager;
import com.whizzosoftware.hobson.bootstrap.api.presence.OSGIPresenceManager;
import com.whizzosoftware.hobson.bootstrap.api.task.OSGITaskManager;
import com.whizzosoftware.hobson.bootstrap.api.user.LocalUserStore;
import com.whizzosoftware.hobson.bootstrap.api.variable.OSGIVariableManager;
import com.whizzosoftware.hobson.bootstrap.rest.HobsonManagerModule;
import com.whizzosoftware.hobson.bootstrap.rest.RootApplication;
import com.whizzosoftware.hobson.bootstrap.rest.SetupApplication;
import com.whizzosoftware.hobson.bootstrap.rest.v1.ApiV1Application;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.ext.guice.SelfInjectingServerResourceModule;
import org.restlet.ext.jetty.HttpServerHelper;
import org.restlet.ext.jetty.HttpsServerHelper;
import org.restlet.ext.slf4j.Slf4jLoggerFacade;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The OSGi activator for the core bundle. This sets up the Hobson foundation such as registering manager objects,
 * starting up Hub advertisements, etc.
 *
 * @author Dan Noguerol
 */
public class Activator extends DependencyActivatorBase {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<org.apache.felix.dm.Component> registeredComponents = new ArrayList<>();
    private ServiceTracker presenceTracker;
    private ServiceTracker applicationTracker;
    private ServiceTracker hubManagerTracker;
    private final Component component = new Component();

    public Activator() {
        super();

        // set the home directory property if not set
        if (System.getProperty(ConfigurationManager.HOBSON_HOME) == null) {
            System.setProperty(ConfigurationManager.HOBSON_HOME, new File("").getAbsolutePath());
        }

        System.out.println("Hobson is starting with home directory: " + System.getProperty(ConfigurationManager.HOBSON_HOME));
    }

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        // set the Netty log factory
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        // create all OSGi managers
        createManagers(manager);

        // listen for the HubManager to be published
        hubManagerTracker = new ServiceTracker(context, HubManager.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference ref) {
                HubManager hubManager = (HubManager)context.getService(ref);

                // create the dependency injector for all REST resources
                Guice.createInjector(new SelfInjectingServerResourceModule(), new HobsonManagerModule(new LocalUserStore(hubManager)));

                // Create the Restlet server
                Engine engine = Engine.getInstance();
                engine.setLoggerFacade(new Slf4jLoggerFacade());
                component.getClients().add(Protocol.CLAP);
                component.getLogService().setEnabled(false);

                // start up the HTTP/HTTPS server
                List<Protocol> protocols = new ArrayList<>();
                Server server;
                if (Boolean.parseBoolean(System.getProperty("useSSL", "false"))) {
                    Engine.getInstance().getRegisteredServers().add(new HttpsServerHelper(null));
                    protocols.add(Protocol.HTTPS);
                    server = new Server(null, protocols, null, 8183, null, "org.restlet.ext.jetty.HttpsServerHelper");
                    component.getServers().add(server);
                    Series<Parameter> parameters = server.getContext().getParameters();
                    parameters.add("keystorePath", "conf/keystore.jks");
                    parameters.add("keystorePassword", "ngZiCkr24ZnbVG");
                    parameters.add("keystoreType", "JKS");
                    parameters.add("keyPassword", "ngZiCkr24ZnbVG");
                } else {
                    Engine.getInstance().getRegisteredServers().add(new HttpServerHelper(null));
                    protocols.add(Protocol.HTTP);
                    server = new Server(null, protocols, null, 8182, null, "org.restlet.ext.jetty.HttpServerHelper");
                    component.getServers().add(server);
                }

                try {
                    // register the root application
                    registerRestletApplication(new RootApplication(), "");

                    // register the REST API application
                    ApiV1Application app = new ApiV1Application();
                    registerRestletApplication(app, ApiV1Application.API_ROOT);

                    // register the setup wizard
                    registerRestletApplication(new SetupApplication(), "/setup");

                    // check for any existing registered Restlet applications
                    ServiceReference[] refs = context.getServiceReferences(Application.class.getName(), null);
                    if (refs != null) {
                        for (ServiceReference ref2 : refs) {
                            registerRestletApplication(ref2);
                        }
                    }

                    // start the Restlet component
                    component.start();

                    // determine web app URL prefix
                    String consoleURI;
                    if (System.getProperty("useSSL") != null) {
                        consoleURI = "https://localhost:8183";
                    } else {
                        consoleURI = "http://localhost:8182";
                    }
                    if (hubManager.getHub(HubContext.createLocal()).getConfiguration().getBooleanPropertyValue(HubConfigurationClass.SETUP_COMPLETE)) {
                        consoleURI += "/console/index.html";
                    } else {
                        consoleURI += "/setup/index.html";
                    }

                    // launch a browser
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(new URI(consoleURI));
                        } else {
                            System.out.println("Hobson is now available at " + consoleURI);
                        }
                    } catch (Throwable t) {
                        logger.warn("Unable to launch web browser", t);
                    }
                } catch (Exception e) {
                    logger.error("Error starting REST API server", e);
                }

                return null;
            }
        };
        hubManagerTracker.open();

        // start listening for new Restlet applications
        applicationTracker = new ServiceTracker(context, Application.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference ref) {
                logger.debug("Detected addition of Restlet service: {}", ref);
                registerRestletApplication(ref);
                return super.addingService(ref);
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                logger.debug("Detected removal of Restlet service: {}", service);
                if (service instanceof Application) {
                    unregisterRestletApplication((Application)service);
                } else {
                    logger.debug("Unknown Restlet service unregistered: {}", service);
                }
                super.removedService(ref, service);
            }
        };
        applicationTracker.open();

        // wait for ConfigurationAdmin to become available to start advertising presence
        presenceTracker = new ServiceTracker(context, ConfigurationManager.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference serviceRef) {
                ServiceReference ref = context.getServiceReference(ConfigurationManager.class.getName());
                if (ref != null) {
                    // start advertisements
                    return context.getService(ref);
                } else {
                    return null;
                }
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                super.removedService(ref, service);
            }
        };
        presenceTracker.open();
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        logger.info("Hobson core is shutting down");

        for (org.apache.felix.dm.Component c : registeredComponents) {
            manager.remove(c);
        }

        component.stop();

        if (presenceTracker != null) {
            presenceTracker.close();
        }
        if (applicationTracker != null) {
            applicationTracker.close();
        }
        if (hubManagerTracker != null) {
            hubManagerTracker.close();
        }
    }

    private void createManagers(DependencyManager manager) {
        // register activity log manager
        org.apache.felix.dm.Component c = manager.createComponent();
        c.setInterface(ActivityLogManager.class.getName(), null);
        c.setImplementation(OSGIActivityLogManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(DeviceManager.class).setRequired(true));
        c.add(createServiceDependency().setService(TaskManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register configuration manager
        c = manager.createComponent();
        c.setInterface(ConfigurationManager.class.getName(), null);
        c.setImplementation(MapDBConfigurationManager.class);
        manager.add(c);
        registeredComponents.add(c);

        // register device manager
        c = manager.createComponent();
        c.setInterface(DeviceManager.class.getName(), null);
        c.setImplementation(OSGIDeviceManager.class);
        c.add(createServiceDependency().setService(ConfigurationManager.class).setRequired(true));
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(VariableManager.class).setRequired(true));
        c.add(createServiceDependency().setService(PluginManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register disco manager
        c = manager.createComponent();
        c.setInterface(DiscoManager.class.getName(), null);
        c.setImplementation(OSGIDiscoManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(PluginManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register event manager
        c = manager.createComponent();
        c.setInterface(EventManager.class.getName(), null);
        c.setImplementation(OSGIEventManager.class);
        c.add(createServiceDependency().setService(EventAdmin.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register hub manager
        c = manager.createComponent();
        c.setInterface(HubManager.class.getName(), null);
        c.setImplementation(OSGIHubManager.class);
        c.add(createServiceDependency().setService(ConfigurationManager.class).setRequired(true));
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register image manager
        c = manager.createComponent();
        c.setInterface(ImageManager.class.getName(), null);
        c.setImplementation(OSGIImageManager.class);
        manager.add(c);
        registeredComponents.add(c);

        // register plugin manager
        c = manager.createComponent();
        c.setInterface(PluginManager.class.getName(), null);
        c.setImplementation(OSGIPluginManager.class);
        c.add(createServiceDependency().setService(ConfigurationManager.class).setRequired(true));
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register presence manager
        c = manager.createComponent();
        c.setInterface(PresenceManager.class.getName(), null);
        c.setImplementation(OSGIPresenceManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(PluginManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register task manager
        c = manager.createComponent();
        c.setInterface(TaskManager.class.getName(), null);
        c.setImplementation(OSGITaskManager.class);
        c.add(createServiceDependency().setService(PluginManager.class).setRequired(true));
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(VariableManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register variable manager
        c = manager.createComponent();
        c.setInterface(VariableManager.class.getName(), null);
        c.setImplementation(OSGIVariableManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(ConfigurationManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);
    }

    private void registerRestletApplication(ServiceReference ref) {
        if (ref != null) {
            Object o = getContext().getService(ref);
            if (o instanceof Application) {
                registerRestletApplication((Application)o, (String)ref.getProperty("path"));
            } else {
                logger.debug("Ignoring unknown published Restlet service: {}", o);
            }
        }
    }

    private void registerRestletApplication(Application a, String path) {
        if (path != null) {
            logger.debug("Registering Restlet application {} with path {}", a, path);
            component.getDefaultHost().attach(path, a);
        } else {
            logger.warn("Restlet application {} registered with no path; ignoring", a);
        }
    }

    private void unregisterRestletApplication(Application a) {
        logger.debug("Unregistering Restlet application {}", a);
        component.getDefaultHost().detach(a);
    }

    private BundleContext getContext() {
        return FrameworkUtil.getBundle(getClass()).getBundleContext();
    }
}
