/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.ActionContext;
import com.whizzosoftware.hobson.api.action.ActionManager;
import com.whizzosoftware.hobson.api.action.HobsonAction;
import com.whizzosoftware.hobson.api.event.EventManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
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

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();

    public void start() {
        String pluginId = FrameworkUtil.getBundle(getClass()).getSymbolicName();

        // publish default actions
        PluginContext ctx = PluginContext.createLocal(pluginId);
        publishAction(new EmailAction(ctx));
        publishAction(new LogAction(ctx));
        publishAction(new SendCommandToDeviceAction(ctx));
    }

    @Override
    public void publishAction(HobsonAction action) {
        String pluginId = action.getContext().getPluginId();
        String actionId = action.getContext().getActionId();
        BundleContext context = BundleUtil.getBundleContext(getClass(), pluginId);

        if (context != null) {
            // register device as a service
            Dictionary<String,String> props = new Hashtable<>();
            if (pluginId == null) {
                logger.error("Unable to publish action with null plugin ID");
            } else if (actionId == null) {
                logger.error("Unable to publish action with null ID");
            } else {
                props.put("pluginId", pluginId);
                props.put("actionId", actionId);

                synchronized (serviceRegistrationMap) {
                    List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
                    if (srl == null) {
                        srl = new ArrayList<>();
                        serviceRegistrationMap.put(pluginId, srl);
                    }
                    srl.add(
                            context.registerService(
                                    HobsonAction.class,
                                    action,
                                    props
                            )
                    );
                }

                // set the action's managers
                action.setVariableManager(variableManager);

                logger.debug("Action {} published", action.getContext());
            }
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public void unpublishAllActions(PluginContext ctx) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(ctx.getPluginId());
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    sr.unregister();
                }
                serviceRegistrationMap.remove(ctx.getPluginId());
            }
        }
    }

    @Override
    public void executeAction(ActionContext ctx, Map<String, Object> properties) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + HobsonAction.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(actionId=" + ctx.getActionId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(HobsonAction.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                HobsonAction action = (HobsonAction)bundleContext.getService(refs[0]);
                action.execute(hubManager, properties);
            } else {
                throw new HobsonRuntimeException("Unable to find action: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error executing action: " + ctx, e);
        }
    }

    @Override
    public Collection<HobsonAction> getAllActions(HubContext ctx) {
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
    public HobsonAction getAction(ActionContext ctx) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            Filter filter = context.createFilter("(&(objectClass=" + HobsonAction.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(actionId=" + ctx.getActionId() + "))");
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
