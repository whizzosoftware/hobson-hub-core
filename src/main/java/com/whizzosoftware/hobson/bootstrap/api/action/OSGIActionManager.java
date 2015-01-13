/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.action.HobsonAction;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An OSGi implementation of ActionManager.
 *
 * @author Dan Noguerol
 */
public class OSGIActionManager implements ActionManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile HubManager hubManager;
    private volatile VariableManager variableManager;
    private volatile EventManager eventManager;

    public void start() {
        String pluginId = FrameworkUtil.getBundle(getClass()).getSymbolicName();

        // publish default actions
        publishAction(new EmailAction(pluginId));
        publishAction(new LogAction(pluginId));
        publishAction(new SendCommandToDeviceAction(pluginId));
    }

    @Override
    public void publishAction(HobsonAction action) {
        BundleContext context = BundleUtil.getBundleContext(getClass(), action.getPluginId());

        if (context != null) {
            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            if (action.getPluginId() == null) {
                logger.error("Unable to publish action with null plugin ID");
            } else if (action.getId() == null) {
                logger.error("Unable to publish action with null ID");
            } else {
                props.put("pluginId", action.getPluginId());
                props.put("actionId", action.getId());
                context.registerService(
                    HobsonAction.class,
                    action,
                    props
                );

                // set the action's managers
                action.setVariableManager(variableManager);

                logger.debug("Action {} published", action.getId());
            }
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public void executeAction(String pluginId, String actionId, Map<String, Object> properties) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + HobsonAction.class.getName() + ")(pluginId=" + pluginId + ")(actionId=" + actionId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(HobsonAction.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                HobsonAction action = (HobsonAction)bundleContext.getService(refs[0]);
                action.execute(hubManager, properties);
            } else {
                throw new HobsonRuntimeException("Unable to find action with ID: " + actionId);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error executing action with ID: " + actionId, e);
        }
    }

    @Override
    public Collection<HobsonAction> getAllActions(String userId, String hubId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<HobsonAction> results = new ArrayList<HobsonAction>();
            ServiceReference[] references = context.getServiceReferences(HobsonAction.class.getName(), null);
            if (references != null) {
                for (ServiceReference ref : references) {
                    results.add((HobsonAction)context.getService(ref));
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving actions", e);
        }
    }

    @Override
    public HobsonAction getAction(String userId, String hubId, String pluginId, String actionId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = context.createFilter("(&(objectClass=" + HobsonAction.class.getName() + ")(pluginId=" + pluginId + ")(actionId=" + actionId + "))");
            ServiceReference[] references = context.getServiceReferences((String)null, filter.toString());
            if (references != null && references.length > 0) {
                return (HobsonAction)context.getService(references[0]);
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action", e);
        }
    }
}
