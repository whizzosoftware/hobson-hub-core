/*
 *******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.action.Action;
import com.whizzosoftware.hobson.api.action.SingleAction;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.device.proxy.HobsonDeviceProxy;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.TaskProvider;
import com.whizzosoftware.hobson.api.variable.DeviceVariableState;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
    private volatile DeviceManager deviceManager;
    private volatile DiscoManager discoManager;
    private volatile EventManager eventManager;
    private volatile ExecutorService executorService;
    private volatile HubManager hubManager;
    private volatile ActionManager actionManager;
    private volatile PluginManager pluginManager;
    private volatile TaskManager taskManager;

    private HobsonPlugin plugin;

    /**
     * Constructor.
     *
     * @param plugin the plugin to wrapper
     */
    public HobsonPluginEventLoopWrapper(HobsonPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when the OSGi service is started. This performs plugin dependency injection and gets the
     * plugin event loop started.
     */
    public void start() {
        logger.debug("Starting plugin: {}", plugin.getContext());

        // inject manager dependencies
        setActionManager(actionManager);
        setDeviceManager(deviceManager);
        setDiscoManager(discoManager);
        setEventManager(eventManager);
        setHubManager(hubManager);
        setPluginManager(pluginManager);
        setTaskManager(taskManager);

        // wait for service to become registered before performing final initialization
        try {
            String filter = "(&(objectclass=" + HobsonPlugin.class.getName() + ")(pluginId=" + getContext().getPluginId() + "))";
            FrameworkUtil.getBundle(getClass()).getBundleContext().addServiceListener(this, filter);
        } catch (InvalidSyntaxException e) {
            logger.error("Error registering service listener for plugin " + getContext(), e);
        }
    }

    /**
     * Called when the OSGi service is stopped. This will stop the plugin event loop.
     */
    public void stop() {
        try {
            logger.debug("Stopping plugin: {}", plugin.getContext());

            final long now = System.currentTimeMillis();

            // remove the service listener
            FrameworkUtil.getBundle(getClass()).getBundleContext().removeServiceListener(this);

            final HubContext ctx = HubContext.createLocal();
            final PluginContext pctx = plugin.getContext();
            final BlockingQueue<Object> blockingQueue = new ArrayBlockingQueue<>(1);

            // stop listening for all events
            eventManager.removeListenerFromAllTopics(ctx, HobsonPluginEventLoopWrapper.this);

            // queue a task for final cleanup
            logger.trace("Queuing final cleanup task for plugin {}", pctx);

            // wait for the async task to complete so that the OSGi framework knows that we've really stopped
            plugin.getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    try {
                        logger.trace("Invoking plugin {} shutdown method", pctx);

                        // shut down the plugin
                        onShutdown();

                        logger.trace("Plugin {} shutdown method returned", pctx);

                        // post plugin stopped event
                        eventManager.postEvent(ctx, new PluginStoppedEvent(now, pctx));
                    } catch (Throwable t) {
                        logger.error("Error shutting down plugin \"" + pctx + "\"", t);
                    }

                    // notify thread that kicked off the stop that the plugin shutdown is complete
                    logger.trace("Sending plugin {} shutdown notify", pctx);
                    blockingQueue.add(new Object());
                }
            });

            try {
                logger.trace("Waiting for final cleanup for plugin {}", pctx);
                Object o = blockingQueue.poll(5, TimeUnit.SECONDS);
                if (o == null) {
                    logger.error("Plugin " + pctx + " failed to stop in the allotted time");
                } else {
                    logger.debug("Shutdown complete for plugin: {}", pctx);
                }
            } catch (InterruptedException ignored) {}
        } catch (Throwable t) {
            logger.error("Error stopping plugin", t);
        }
    }

    /*
     * EventManagerListener methods
     */

    @Override
    public void onHobsonEvent(final HobsonEvent event) {
        plugin.getEventLoopExecutor().executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    // ignore the event if the plugin has been stopped
                    if (plugin != null && plugin.getContext() != null && plugin.getContext().getPluginId() != null) {
                        String pluginId = plugin.getContext().getPluginId();
                        if (event instanceof PluginConfigurationUpdateEvent && pluginId.equals(((PluginConfigurationUpdateEvent)event).getPluginId())) {
                            PluginConfigurationUpdateEvent pcue = (PluginConfigurationUpdateEvent)event;
                            logger.trace("Dispatching device config update for {} to runtime", pcue.getPluginId());
                            plugin.onPluginConfigurationUpdate(pcue.getConfiguration());
                        } else if (event instanceof DeviceConfigurationUpdateEvent && pluginId.equals(((DeviceConfigurationUpdateEvent)event).getPluginId())) {
                            DeviceConfigurationUpdateEvent dcue = (DeviceConfigurationUpdateEvent)event;
                            logger.trace("Dispatching device config update for {}:{} to runtime", dcue.getPluginId(), dcue.getDeviceId());
                            plugin.onDeviceConfigurationUpdate(dcue.getDeviceId(), dcue.getConfiguration());
                        } else {
                            logger.trace("Dispatching event to plugin {}: {}", pluginId, event);
                            plugin.onHobsonEvent(event);
                        }
                    } else {
                        logger.error("Error processing event for plugin " + plugin + ": " + event);
                    }
                } catch (Throwable e) {
                    logger.error("An error occurred processing an event", e);
                }
            }
        });
    }

    /*
     * Hobson plugin interface methods --  these all pass-through to the real plugin implementation
     */

    @Override
    public PluginContext getContext() {
        return plugin.getContext();
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
    public PropertyContainerClass getConfigurationClass() {
        return plugin.getConfigurationClass();
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
            String[] otherTopics = plugin.getRuntime().getEventTopics();
            if (otherTopics != null) {
                otherTopicsCount = otherTopics.length;
            }
            String[] topics = new String[otherTopicsCount + 1];
            topics[0] = EventTopics.STATE_TOPIC; // all plugins need to listen for state events
            if (otherTopicsCount > 0) {
                System.arraycopy(otherTopics, 0, topics, 1, otherTopicsCount);
            }
            eventManager.addListener(plugin.getContext().getHubContext(), this, topics);

            // start the event loop
            Future f = getRuntime().submitInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // start the plugin
                    getRuntime().onStartup(pluginManager.getLocalPluginConfiguration(plugin.getContext()));

                    // post plugin started event
                    eventManager.postEvent(plugin.getContext().getHubContext(), new PluginStartedEvent(System.currentTimeMillis(), getContext()));

                    // schedule the refresh callback if the plugin's refresh interval > 0
                    if (plugin.getRuntime().getRefreshInterval() > 0) {
                        getRuntime().scheduleAtFixedRateInEventLoop(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    getRuntime().onRefresh();
                                } catch (Throwable e) {
                                    logger.error("Error refreshing plugin: " + plugin.getContext(), e);
                                }
                            }
                        }, 0, plugin.getRuntime().getRefreshInterval(), TimeUnit.SECONDS);
                    }

                    logger.debug("Startup complete for plugin: {}", plugin.getContext());
                }
            });

            // wait for the async task to complete so that the OSGi framework knows that we've really started
            try {
                f.get();
            } catch (Throwable e) {
                logger.error("Error waiting for plugin to start", e);
            }
        }
    }

    public HobsonPlugin getPlugin() {
        return plugin;
    }
}
