/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap;

import com.google.inject.Guice;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.api.action.EmailAction;
import com.whizzosoftware.hobson.bootstrap.api.action.LogAction;
import com.whizzosoftware.hobson.bootstrap.api.action.OSGIActionManager;
import com.whizzosoftware.hobson.bootstrap.api.action.SendCommandToDeviceAction;
import com.whizzosoftware.hobson.bootstrap.api.device.OSGIDeviceManager;
import com.whizzosoftware.hobson.bootstrap.api.disco.OSGIDiscoManager;
import com.whizzosoftware.hobson.bootstrap.api.event.OSGIEventManager;
import com.whizzosoftware.hobson.bootstrap.api.hub.OSGIHubManager;
import com.whizzosoftware.hobson.bootstrap.api.plugin.OSGIPluginManager;
import com.whizzosoftware.hobson.bootstrap.api.presence.OSGIPresenceManager;
import com.whizzosoftware.hobson.bootstrap.api.task.OSGITaskManager;
import com.whizzosoftware.hobson.bootstrap.api.variable.OSGIVariableManager;
import com.whizzosoftware.hobson.bootstrap.discovery.Advertiser;
import com.whizzosoftware.hobson.bootstrap.rest.HobsonManagerModule;
import com.whizzosoftware.hobson.bootstrap.rest.HobsonVerifier;
import com.whizzosoftware.hobson.bootstrap.rest.RootApplication;
import com.whizzosoftware.hobson.bootstrap.rest.SetupApplication;
import com.whizzosoftware.hobson.rest.v1.ApiV1Application;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Server;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.engine.Engine;
import org.restlet.ext.guice.SelfInjectingServerResourceModule;
import org.restlet.ext.jetty.HttpServerHelper;
import org.restlet.ext.jetty.HttpsServerHelper;
import org.restlet.ext.slf4j.Slf4jLoggerFacade;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
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
    private Advertiser advertiser;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        logger.info("Hobson core is starting");

        // set the Netty log factory
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        // create all OSGi managers
        createManagers(manager);

        // create the dependency injector for all REST resources
        Guice.createInjector(new SelfInjectingServerResourceModule(), new HobsonManagerModule());

        // Create the Restlet server
        Engine engine = Engine.getInstance();
        engine.setLoggerFacade(new Slf4jLoggerFacade());
        component.getClients().add(Protocol.CLAP);
        component.getLogService().setEnabled(false);

        // listen for the HubManager to be published
        hubManagerTracker = new ServiceTracker(context, HubManager.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference ref) {
                HubManager hubManager = (HubManager)context.getService(ref);

                // publish the default actions
                publishDefaultActions(
                    context.getBundle().getSymbolicName(),
                    (ActionManager)context.getService(context.getServiceReference(ActionManager.class.getName())),
                    hubManager
                );

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

                // create the authenticator
                ChallengeAuthenticator authenticator = new ChallengeAuthenticator(server.getContext(), ChallengeScheme.HTTP_BASIC, "Hobson");
                authenticator.setVerifier(new HobsonVerifier(hubManager));

                try {
                    // register the root application
                    registerRestletApplication(new RootApplication(), "");

                    // register the REST API application
                    registerRestletApplication(new ApiV1Application("/api/v1", authenticator), "/api/v1");

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
                registerRestletApplication(ref);
                return null;
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                if (service instanceof Application) {
                    logger.warn("Removing Restlet application: {}", service);
                    component.getDefaultHost().detach((Application)service);
                }
            }
        };
        applicationTracker.open();

        // wait for ConfigurationAdmin to become available to start advertising presence
        presenceTracker = new ServiceTracker(context, ConfigurationAdmin.class.getName(), null) {
            @Override
            public Object addingService(ServiceReference serviceRef) {
                ServiceReference ref = context.getServiceReference(ConfigurationAdmin.class.getName());
                if (ref != null) {
                    // start advertisements
                    ConfigurationAdmin configAdmin = (ConfigurationAdmin) context.getService(ref);
                    startAdvertiser();
                    return configAdmin;
                } else {
                    return null;
                }
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
            }
        };
        presenceTracker.open();
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        for (org.apache.felix.dm.Component c : registeredComponents) {
            manager.remove(c);
        }

        if (advertiser != null) {
            advertiser.stop();
        }
        if (component != null) {
            component.stop();
        }
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
        // register action manager
        org.apache.felix.dm.Component c = manager.createComponent();
        c.setInterface(ActionManager.class.getName(), null);
        c.setImplementation(OSGIActionManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        c.add(createServiceDependency().setService(VariableManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register device manager
        c = manager.createComponent();
        c.setInterface(DeviceManager.class.getName(), null);
        c.setImplementation(OSGIDeviceManager.class);
        c.add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true));
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
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
        c.add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true));
        c.add(createServiceDependency().setService(ActionManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register plugin manager
        c = manager.createComponent();
        c.setInterface(PluginManager.class.getName(), null);
        c.setImplementation(OSGIPluginManager.class);
        c.add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register presence manager
        c = manager.createComponent();
        c.setInterface(PresenceManager.class.getName(), null);
        c.setImplementation(OSGIPresenceManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register task manager
        c = manager.createComponent();
        c.setInterface(TaskManager.class.getName(), null);
        c.setImplementation(OSGITaskManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);

        // register variable manager
        c = manager.createComponent();
        c.setInterface(VariableManager.class.getName(), null);
        c.setImplementation(OSGIVariableManager.class);
        c.add(createServiceDependency().setService(EventManager.class).setRequired(true));
        manager.add(c);
        registeredComponents.add(c);
    }

    private void publishDefaultActions(String pluginId, ActionManager manager, HubManager hubManager) {
        manager.publishAction(new EmailAction(pluginId, hubManager.getHubEmailConfiguration(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB)));
        manager.publishAction(new LogAction(pluginId));
        manager.publishAction(new SendCommandToDeviceAction(pluginId));
    }

    private void startAdvertiser() {
        try {
            // determine server name
            String hubName = "New Hobson Hub";
            Configuration config = getConfiguration();
            if (config != null) {
                Dictionary d = config.getProperties();
                if (d != null) {
                    String name = (String) d.get("hub.name");
                    if (name != null) {
                        hubName = name;
                    }
                }
            }

            // start advertiser
            advertiser = new Advertiser(hubName);
            advertiser.start();
        } catch (Exception e) {
            logger.error("Error starting advertisements", e);
        }
    }

    private void registerRestletApplication(ServiceReference ref) {
        if (ref != null) {
            registerRestletApplication((Application)getContext().getService(ref), (String)ref.getProperty("path"));
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

    private BundleContext getContext() {
        return FrameworkUtil.getBundle(getClass()).getBundleContext();
    }

    private Configuration getConfiguration() throws IOException {
        BundleContext bundleContext = getContext();
        ServiceReference sr = bundleContext.getServiceReference(ConfigurationAdmin.class.getName());
        ConfigurationAdmin configAdmin = (ConfigurationAdmin)bundleContext.getService(sr);
        return configAdmin.getConfiguration("com.whizzosoftware.hobson.general", "com.whizzosoftware.hobson.hub.hobson-hub-core");
    }
}
