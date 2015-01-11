/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.config.ConfigurationPropertyMetaData;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A class that encapsulates a Hobson plugin class in order to handle OSGi lifecycle events and provide
 * the plugin event loop. This implements HobsonPlugin so it can will appear to the OSGi runtime as
 * an actual plugin while it intercepts the OSGi lifecycle callbacks.
 *
 * @author Dan Noguerol
 */
public class HobsonPluginEventLoopWrapper implements HobsonPlugin, EventListener, ServiceListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // these will be dependency injected by the OSGi runtime
    private volatile ActionManager actionManager;
    private volatile DeviceManager deviceManager;
    private volatile DiscoManager discoManager;
    private volatile EventManager eventManager;
    private volatile ExecutorService executorService;
    private volatile HubManager hubManager;
    private volatile PluginManager pluginManager;
    private volatile TaskManager taskManager;
    private volatile VariableManager variableManager;

    private HobsonPlugin plugin;

    /**
     * Constructor.
     *
     * @param plugin the plugin to wrapper
     */
    public HobsonPluginEventLoopWrapper(HobsonPlugin plugin) {
        if (plugin != null) {
            this.plugin = plugin;
        } else {
            throw new HobsonRuntimeException("Passed a null plugin to HobsonPluginEventLoopWrapper");
        }
    }

    /**
     * Called when the OSGi service is started. This performs plugin dependency injection and gets the
     * plugin event loop started.
     */
    public void start() {
        // inject manager dependencies
        getRuntime().setActionManager(actionManager);
        getRuntime().setDeviceManager(deviceManager);
        getRuntime().setDiscoManager(discoManager);
        getRuntime().setEventManager(eventManager);
        getRuntime().setHubManager(hubManager);
        getRuntime().setPluginManager(pluginManager);
        getRuntime().setTaskManager(taskManager);
        getRuntime().setVariableManager(variableManager);

        // wait for service to become registered before performing final initialization
        try {
            String filter = "(&(objectclass=" + HobsonPlugin.class.getName() + ")(pluginId=" + getId() + "))";
            FrameworkUtil.getBundle(getClass()).getBundleContext().addServiceListener(this, filter);
        } catch (InvalidSyntaxException e) {
            logger.error("Error registering service listener for plugin " + getId(), e);
        }
    }

    /**
     * Called when the OSGi service is stopped. This will stop the plugin event loop.
     */
    public void stop() {
        // remove the service listener
        FrameworkUtil.getBundle(getClass()).getBundleContext().removeServiceListener(this);

        // shutdown the plugin
        Future f = plugin.getRuntime().submitInEventLoop(new Runnable() {
            @Override
            public void run() {
            // stop listening for all events
            eventManager.removeListenerFromAllTopics(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, HobsonPluginEventLoopWrapper.this);

            // unpublish all variables published by this plugin's devices
            variableManager.getPublisher().unpublishAllPluginVariables(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, getId());

            // stop all devices
            deviceManager.getPublisher().unpublishAllDevices(plugin);

            // shut down the plugin
            getRuntime().onShutdown();

            // post plugin stopped event
            eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new PluginStoppedEvent(getId()));

            // drop reference
            plugin = null;
            }
        });

        // wait for the async task to complete so that the OSGi framework knows that we've really stopped
        try {
            f.get();
        } catch (Exception e) {
            logger.error("Error waiting for plugin to stop", e);
        }
    }

    /*
     * EventManagerListener methods
     */

    @Override
    public void onHobsonEvent(final HobsonEvent event) {
        plugin.getRuntime().executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    if (event instanceof PluginConfigurationUpdateEvent && plugin.getId().equals(((PluginConfigurationUpdateEvent)event).getPluginId())) {
                        PluginConfigurationUpdateEvent pcue = (PluginConfigurationUpdateEvent)event;
                        plugin.getRuntime().onPluginConfigurationUpdate(pcue.getConfiguration());
                    } else if (event instanceof DeviceConfigurationUpdateEvent && plugin.getId().equals(((DeviceConfigurationUpdateEvent)event).getPluginId())) {
                        DeviceConfigurationUpdateEvent dcue = (DeviceConfigurationUpdateEvent)event;
                        plugin.getRuntime().onDeviceConfigurationUpdate(dcue.getDeviceId(), dcue.getConfiguration());
                    } else {
                        plugin.getRuntime().onHobsonEvent(event);
                    }
                } catch (Exception e) {
                    logger.error("An error occurred processing an event", e);
                }
            }
        });
    }

    /*
     * Hobson plugin interface methods --  these all pass-through to the real plugin implementation
     */

    @Override
    public String getId() {
        return plugin.getId();
    }

    @Override
    public String getName() {
        return plugin.getName();
    }

    @Override
    public String getVersion() {
        return plugin.getVersion();
    }

    @Override
    public PluginStatus getStatus() {
        return plugin.getStatus();
    }

    @Override
    public long getRefreshInterval() {
        return plugin.getRefreshInterval();
    }

    @Override
    public String[] getEventTopics() {
        return plugin.getEventTopics();
    }

    @Override
    public Collection<ConfigurationPropertyMetaData> getConfigurationPropertyMetaData() {
        return plugin.getConfigurationPropertyMetaData();
    }

    @Override
    public PluginType getType() {
        return plugin.getType();
    }

    @Override
    public boolean isConfigurable() {
        return plugin.isConfigurable();
    }

    @Override
    public HobsonPluginRuntime getRuntime() {
        return plugin.getRuntime();
    }

    @Override
    public void serviceChanged(ServiceEvent serviceEvent) {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            // register plugin for necessary event topics
            int otherTopicsCount = 0;
            String[] otherTopics = plugin.getEventTopics();
            if (otherTopics != null) {
                otherTopicsCount = otherTopics.length;
            }
            String[] topics = new String[otherTopicsCount + 2];
            topics[0] = EventTopics.VARIABLES_TOPIC; // all plugins need to listen for variable events
            topics[1] = EventTopics.CONFIG_TOPIC; // all plugins need to listen for configuration events
            if (otherTopicsCount > 0) {
                System.arraycopy(otherTopics, 0, topics, 2, otherTopicsCount);
            }
            eventManager.addListener(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, this, topics);

            // start the event loop
            Future f = getRuntime().submitInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // start the plugin
                    getRuntime().onStartup(pluginManager.getPluginConfiguration(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, plugin).getPropertyDictionary());

                    // post plugin started event
                    eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new PluginStartedEvent(getId()));

                    // schedule the refresh callback if the plugin's refresh interval > 0
                    if (plugin.getRefreshInterval() > 0) {
                        getRuntime().scheduleAtFixedRateInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    getRuntime().onRefresh();
                                } catch (Exception e) {
                                    logger.error("Error refreshing plugin " + plugin.getId(), e);
                                }
                            }
                        }, 0, plugin.getRefreshInterval(), TimeUnit.SECONDS);
                    }
                }
            });

            // wait for the async task to complete so that the OSGi framework knows that we've really started
            try {
                f.get();
            } catch (Exception e) {
                logger.error("Error waiting for plugin to start", e);
            }
        }
    }
}
