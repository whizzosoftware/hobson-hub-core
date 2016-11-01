/*
 *******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.ActionClass;
import com.whizzosoftware.hobson.api.action.ActionClassProvider;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.action.store.ActionStore;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An OSGi implementation of ActionClassProvider.
 *
 * @author Dan Noguerol
 */
public class OSGIActionClassProvider implements ActionClassProvider {
    private BundleContext bundleContext;
    private ActionManager actionManager;

    public OSGIActionClassProvider(BundleContext bundleContext, ActionManager actionManager) {
        this.bundleContext = bundleContext;
        this.actionManager = actionManager;
    }

    @Override
    public ActionClass getActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(type=actionClass)(classId=" + ctx.getContainerClassId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (ActionClass)bundleContext.getService(refs[0]);
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public Collection<PropertyContainerClassContext> getActionSetClassContexts(String actionSetId) {
        List<PropertyContainerClassContext> results = new ArrayList<>();
        PropertyContainerSet pcs = actionManager.getActionSet(HubContext.createLocal(), actionSetId);
        for (PropertyContainer pc : pcs.getProperties()) {
            results.add(pc.getContainerClassContext());
        }
        return results;
    }
}
