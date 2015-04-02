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
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.disco.DiscoManager;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.image.ImageManager;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.bootstrap.rest.v1.LocalAuthorizer;
import com.whizzosoftware.hobson.rest.v1.Authorizer;
import com.whizzosoftware.hobson.rest.v1.util.HATEOASLinkHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * A Guice module for injecting Hobson manager instances.
 *
 * @author Dan Noguerol
 */
public class HobsonManagerModule extends AbstractModule {
    private HATEOASLinkHelper linkHelper = new HATEOASLinkHelper();
    private Authorizer authorizer = new LocalAuthorizer();

    @Override
    protected void configure() {
    }

    @Provides
    public Authorizer providerAuthorizer() {
        return authorizer;
    }

    @Provides
    public HATEOASLinkHelper provideLinkHelper() {
        return linkHelper;
    }

    @Provides
    public ActionManager provideActionManager() {
        return (ActionManager)getManager(ActionManager.class);
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
