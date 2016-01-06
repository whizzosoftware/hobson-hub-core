/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.*;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.telemetry.TelemetryManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;
import io.netty.util.concurrent.Future;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private volatile PluginManager pluginManager;
    private volatile TaskManager taskManager;
    private volatile TelemetryManager telemetryManager;
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
        logger.debug("Starting plugin: {}", plugin.getContext());

        // inject manager dependencies
        getRuntime().setDeviceManager(deviceManager);
        getRuntime().setDiscoManager(discoManager);
        getRuntime().setEventManager(eventManager);
        getRuntime().setHubManager(hubManager);
        getRuntime().setPluginManager(pluginManager);
        getRuntime().setTaskManager(taskManager);
        getRuntime().setTelemetryManager(telemetryManager);
        getRuntime().setVariableManager(variableManager);

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
        logger.debug("Stopping plugin: {}", plugin.getContext());

        final long now = System.currentTimeMillis();

        // remove the service listener
        FrameworkUtil.getBundle(getClass()).getBundleContext().removeServiceListener(this);

        final HubContext ctx = HubContext.createLocal();
        final PluginContext pctx = plugin.getContext();
        final Object mutex = new Object();

        // stop listening for all events
        eventManager.removeListenerFromAllTopics(ctx, HobsonPluginEventLoopWrapper.this);

        // unpublish all variables published by this plugin's devices
        variableManager.unpublishAllPluginVariables(plugin.getContext());

        // stop all devices
        deviceManager.unpublishAllDevices(pctx, plugin.getRuntime().getEventLoopExecutor());

        // queue a task for final cleanup
        logger.trace("Queuing final cleanup task");
        plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
            @Override
            public void run() {
                logger.trace("All devices have shut down; performing final cleanup");

                // shut down the plugin
                getRuntime().onShutdown();

                // post plugin stopped event
                eventManager.postEvent(ctx, new PluginStoppedEvent(now, getContext()));

                // release reference
                plugin = null;

                // notify thread that kicked off the stop that the plugin shutdown is complete
                synchronized (mutex) {
                    mutex.notify();
                }
            }
        });

        // wait for the async task to complete so that the OSGi framework knows that we've really stopped
        synchronized (mutex) {
            try {
                logger.trace("Waiting for final cleanup");
                mutex.wait();
            } catch (InterruptedException ignored) {}
        }

        logger.debug("Shutdown complete for plugin: {}", pctx);
    }

    /*
     * EventManagerListener methods
     */

    @Override
    public void onHobsonEvent(final HobsonEvent event) {
        plugin.getRuntime().submitInEventLoop(new Runnable() {
            @Override
            public void run() {
                try {
                    // ignore the event if the plugin has been stopped
                    if (plugin != null && plugin.getContext() != null && plugin.getContext().getPluginId() != null) {
                        String pluginId = plugin.getContext().getPluginId();
                        if (event instanceof PluginConfigurationUpdateEvent && pluginId.equals(((PluginConfigurationUpdateEvent)event).getPluginId())) {
                            PluginConfigurationUpdateEvent pcue = (PluginConfigurationUpdateEvent)event;
                            logger.trace("Dispatching device config update for {} to runtime", pcue.getPluginId());
                            plugin.getRuntime().onPluginConfigurationUpdate(pcue.getConfiguration());
                        } else if (event instanceof DeviceConfigurationUpdateEvent && pluginId.equals(((DeviceConfigurationUpdateEvent)event).getPluginId())) {
                            DeviceConfigurationUpdateEvent dcue = (DeviceConfigurationUpdateEvent)event;
                            logger.trace("Dispatching device config update for {}:{} to runtime", dcue.getPluginId(), dcue.getDeviceId());
                            plugin.getRuntime().onDeviceConfigurationUpdate(DeviceContext.create(plugin.getContext(), dcue.getDeviceId()), dcue.getConfiguration());
                        } else if (event instanceof VariableUpdateRequestEvent) {
                            VariableUpdateRequestEvent dcue = (VariableUpdateRequestEvent)event;
                            for (VariableUpdate update : dcue.getUpdates()) {
                                if (pluginId.equals(update.getPluginId())) {
                                    logger.trace("Dispatching variable update request for {}:{} to runtime", update.getPluginId(), update.getDeviceId());
                                    plugin.getRuntime().onSetDeviceVariable(DeviceContext.create(plugin.getContext(), update.getDeviceId()), update.getName(), update.getValue());
                                }
                            }
                        } else {
                            logger.trace("Dispatching event to plugin {}: {}", pluginId, event);
                            plugin.getRuntime().onHobsonEvent(event);
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
