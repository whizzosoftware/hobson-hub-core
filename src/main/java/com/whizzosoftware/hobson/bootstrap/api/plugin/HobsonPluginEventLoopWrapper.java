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
import com.whizzosoftware.hobson.api.action.HobsonAction;
import com.whizzosoftware.hobson.api.config.ConfigurationPropertyMetaData;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.TaskProvider;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A class that encapsulates a Hobson plugin class in order to handle OSGi lifecycle events and provide
 * the plugin event loop. This implements HobsonPlugin so it can will appear to the OSGi runtime as
 * an actual plugin while it intercepts the OSGi lifecycle callbacks.
 *
 * @author Dan Noguerol
 */
public class HobsonPluginEventLoopWrapper implements HobsonPlugin, PluginConfigurationListener, EventListener, ServiceListener {
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
        // register plugin for configuration updates
        pluginManager.registerForPluginConfigurationUpdates(getId(), this);

        // inject manager dependencies
        setActionManager(actionManager);
        setDeviceManager(deviceManager);
        setDiscoManager(discoManager);
        setEventManager(eventManager);
        setHubManager(hubManager);
        setPluginManager(pluginManager);
        setTaskManager(taskManager);
        setVariableManager(variableManager);

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
        Future f = plugin.submitInEventLoop(new Runnable() {
            @Override
            public void run() {
                // stop listening for configuration changes
                pluginManager.unregisterForPluginConfigurationUpdates(getId(), HobsonPluginEventLoopWrapper.this);

                // stop listening for variable events
                eventManager.removeListenerFromAllTopics(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, HobsonPluginEventLoopWrapper.this);

                // unpublish all variables published by this plugin's devices
                variableManager.unpublishAllPluginVariables(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, getId());

                // stop all devices
                deviceManager.unpublishAllDevices(plugin);

                // shut down the plugin
                onShutdown();

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
     * PluginConfigurationListener methods
     */

    @Override
    public void onPluginConfigurationUpdate(final Dictionary config) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.onPluginConfigurationUpdate(config);
                } catch (Exception e) {
                    logger.error("An error occurred updating plugin configuration", e);
                }
            }
        });
    }

    /*
     * DeviceConfigurationListener methods
     */

    @Override
    public void onDeviceConfigurationUpdate(final String deviceId, final Dictionary config) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.onDeviceConfigurationUpdate(deviceId, config);
                } catch (Exception e) {
                    logger.error("An error occurred updating device configuration", e);
                }
            }
        });
    }

    /*
     * EventManagerListener methods
     */

    @Override
    public void onHobsonEvent(final HobsonEvent event) {
        plugin.executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.onHobsonEvent(event);
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
    public void onStartup(Dictionary config) {
        plugin.onStartup(config);
    }

    @Override
    public void onShutdown() {
        plugin.onShutdown();
    }

    @Override
    public void onRefresh() {
        plugin.onRefresh();
    }

    @Override
    public void setDeviceManager(DeviceManager deviceManager) {
        plugin.setDeviceManager(deviceManager);
    }

    @Override
    public void setVariableManager(VariableManager deviceManager) {
        plugin.setVariableManager(deviceManager);
    }

    @Override
    public void setDiscoManager(DiscoManager discoManager) {
        plugin.setDiscoManager(discoManager);
    }

    @Override
    public void setEventManager(EventManager eventManager) {
        plugin.setEventManager(eventManager);
    }

    @Override
    public void setHubManager(HubManager hubManager) {
        plugin.setHubManager(hubManager);
    }

    @Override
    public void setPluginManager(PluginManager pluginManager) {
        plugin.setPluginManager(pluginManager);
    }

    @Override
    public void setTaskManager(TaskManager taskManager) {
        plugin.setTaskManager(taskManager);
    }

    @Override
    public void setActionManager(ActionManager actionManager) {
        plugin.setActionManager(actionManager);
    }

    @Override
    public void executeInEventLoop(Runnable runnable) {
        plugin.executeInEventLoop(runnable);
    }

    @Override
    public Future submitInEventLoop(Runnable runnable) {
        return plugin.submitInEventLoop(runnable);
    }

    @Override
    public void scheduleAtFixedRateInEventLoop(Runnable runnable, long initialDelay, long time, TimeUnit unit) {
        plugin.scheduleAtFixedRateInEventLoop(runnable, initialDelay, time, unit);
    }

    @Override
    public void setDeviceConfigurationProperty(String id, String name, Object value, boolean overwrite) {
        plugin.setDeviceConfigurationProperty(id, name, value, overwrite);
    }

    @Override
    public void publishGlobalVariable(HobsonVariable variable) {
        plugin.publishGlobalVariable(variable);
    }

    @Override
    public void publishDeviceVariable(String deviceId, HobsonVariable variable) {
        plugin.publishDeviceVariable(deviceId, variable);
    }

    @Override
    public void publishTaskProvider(TaskProvider taskProvider) {
        plugin.publishTaskProvider(taskProvider);
    }

    @Override
    public void publishAction(HobsonAction action) {
        plugin.publishAction(action);
    }

    @Override
    public void fireVariableUpdateNotifications(List<VariableUpdate> updates) {
        plugin.fireVariableUpdateNotifications(updates);
    }

    @Override
    public void fireVariableUpdateNotification(VariableUpdate variableUpdate) {
        plugin.fireVariableUpdateNotification(variableUpdate);
    }

    @Override
    public HobsonVariable getDeviceVariable(String deviceId, String variableName) {
        return plugin.getDeviceVariable(deviceId, variableName);
    }

    @Override
    public void onSetDeviceVariable(String deviceId, String variableName, Object value) {
        plugin.onSetDeviceVariable(deviceId, variableName, value);
    }

    @Override
    public long getRefreshInterval() {
        return plugin.getRefreshInterval();
    }

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
    public void serviceChanged(ServiceEvent serviceEvent) {
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            // register plugin for necessary event topics
            int otherTopicsCount = 0;
            String[] otherTopics = plugin.getEventTopics();
            if (otherTopics != null) {
                otherTopicsCount = otherTopics.length;
            }
            String[] topics = new String[otherTopicsCount + 1];
            topics[0] = EventTopics.VARIABLES_TOPIC; // all plugins need to listen for variable events
            if (otherTopicsCount > 0) {
                System.arraycopy(otherTopics, 0, topics, 1, otherTopicsCount);
            }
            eventManager.addListener(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, this, topics);

            // start the event loop
            Future f = plugin.submitInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // start the plugin
                    onStartup(pluginManager.getPluginConfiguration(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, plugin).getPropertyDictionary());

                    // post plugin started event
                    eventManager.postEvent(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, new PluginStartedEvent(getId()));

                    // schedule the refresh callback if the plugin's refresh interval > 0
                    if (plugin.getRefreshInterval() > 0) {
                        plugin.scheduleAtFixedRateInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    plugin.onRefresh();
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
