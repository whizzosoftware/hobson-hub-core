/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.whizzosoftware.hobson.api.activity.ActivityLogManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.image.ImageManager;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.telemetry.TelemetryManager;
import com.whizzosoftware.hobson.api.user.UserStore;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.rest.v1.LocalAuthorizer;
import com.whizzosoftware.hobson.rest.Authorizer;
import com.whizzosoftware.hobson.rest.v1.util.HATEOASLinkProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * A Guice module for injecting Hobson manager instances.
 *
 * @author Dan Noguerol
 */
public class HobsonManagerModule extends AbstractModule {
    private UserStore userStore;
    private HATEOASLinkProvider linkProvider = new HATEOASLinkProvider();
    private Authorizer authorizer = new LocalAuthorizer();

    public HobsonManagerModule(UserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    protected void configure() {
    }

    @Provides
    public Authorizer providerAuthorizer() {
        return authorizer;
    }

    @Provides
    public HATEOASLinkProvider provideLinkProvider() {
        return linkProvider;
    }

    @Provides
    public ActivityLogManager provideActivityLogManager() {
        return (ActivityLogManager)getManager(ActivityLogManager.class);
    }

    @Provides
    public DeviceManager provideDeviceManager() {
        return (DeviceManager)getManager(DeviceManager.class);
    }

    @Provides
    public DiscoManager provideDiscoManager() {
        return (DiscoManager)getManager(DiscoManager.class);
    }

    @Provides
    public EventManager provideEventManager() {
        return (EventManager)getManager(EventManager.class);
    }

    @Provides
    public ImageManager provideImageManager() {
        return (ImageManager)getManager(ImageManager.class);
    }

    @Provides
    public HubManager provideSetupManager() {
        return (HubManager)getManager(HubManager.class);
    }

    @Provides
    public PluginManager providePluginManager() {
        return (PluginManager)getManager(PluginManager.class);
    }

    @Provides
    public PresenceManager providePresenceManager() {
        return (PresenceManager)getManager(PresenceManager.class);
    }

    @Provides
    public TaskManager provideTaskManager() {
        return (TaskManager)getManager(TaskManager.class);
    }

    @Provides
    public TelemetryManager provideTelemetryManager() {
        return (TelemetryManager)getManager(TelemetryManager.class);
    }

    @Provides
    public UserStore provideUserStore() {
        return userStore;
    }

    @Provides
    public VariableManager provideVariableManager() {
        return (VariableManager)getManager(VariableManager.class);
    }

    private Object getManager(Class clazz) {
        BundleContext ctx = FrameworkUtil.getBundle(getClass()).getBundleContext();
        ServiceReference ref = ctx.getServiceReference(clazz.getName());
        if (ref != null) {
            return ctx.getService(ref);
        } else {
            return null;
        }
    }
}
