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
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.activity.ActivityLogManager;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.image.ImageManager;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.data.StubDataStreamManager;
import com.whizzosoftware.hobson.api.data.DataStreamManager;
import com.whizzosoftware.hobson.api.user.UserStore;
import com.whizzosoftware.hobson.bootstrap.api.user.LocalUserStore;
import com.whizzosoftware.hobson.bootstrap.rest.oidc.LocalOIDCConfigProvider;
import com.whizzosoftware.hobson.dto.context.DTOBuildContextFactory;
import com.whizzosoftware.hobson.rest.oidc.OIDCConfigProvider;
import com.whizzosoftware.hobson.rest.v1.util.MediaProxyHandler;
import com.whizzosoftware.hobson.rest.v1.util.RestResourceIdProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.restlet.security.Authorizer;

/**
 * A Guice module for injecting Hobson manager instances.
 *
 * @author Dan Noguerol
 */
public class HobsonManagerModule extends AbstractModule {
    private HubManager hubManager;

    public HobsonManagerModule(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    protected void configure() {
        bind(Authorizer.class).to(LocalAuthorizer.class).asEagerSingleton();
        bind(OIDCConfigProvider.class).to(LocalOIDCConfigProvider.class).asEagerSingleton();
        bind(IdProvider.class).to(RestResourceIdProvider.class).asEagerSingleton();
        bind(MediaProxyHandler.class).to(LocalDeviceMediaProxyHandler.class).asEagerSingleton();
        bind(DTOBuildContextFactory.class).to(DTOBuildContextFactoryImpl.class);
        bind(UserStore.class).to(LocalUserStore.class).asEagerSingleton();
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
    public HubManager provideHubManager() {
        return hubManager;
    }

    @Provides
    public ActionManager provideActionManager() {
        return (ActionManager)getManager(ActionManager.class);
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
    public DataStreamManager provideDataStreamManager() {
        DataStreamManager tm = (DataStreamManager)getManager(DataStreamManager.class);
        return tm != null ? tm : new StubDataStreamManager();
    }

    private Object getManager(Class clazz) {
        BundleContext ctx = FrameworkUtil.getBundle(getClass()).getBundleContext();
        ServiceReference ref;
        int count = 0;

        // since requests may still come in while the runtime is initializing, we re-try for up to two seconds to
        // obtain a manager instance (returning null immediately will cause a Guice error)
        while (count < 20) {
            ref = ctx.getServiceReference(clazz.getName());
            if (ref != null) {
                return ctx.getService(ref);
            } else {
                if (clazz.equals(DataStreamManager.class)) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
            count++;
        }

        return null;
    }
}
