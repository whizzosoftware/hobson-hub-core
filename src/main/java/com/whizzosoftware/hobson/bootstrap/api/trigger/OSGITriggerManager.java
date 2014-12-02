/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.trigger;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import com.whizzosoftware.hobson.api.trigger.HobsonTrigger;
import com.whizzosoftware.hobson.api.trigger.TriggerException;
import com.whizzosoftware.hobson.api.trigger.TriggerManager;
import com.whizzosoftware.hobson.api.trigger.TriggerProvider;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * An OSGi implementation of TriggerManager.
 *
 * @author Dan Noguerol
 */
public class OSGITriggerManager implements TriggerManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;

    @Override
    public void publishTriggerProvider(String userId, String hubId, TriggerProvider provider) {
        BundleContext context = BundleUtil.getBundleContext(getClass(), provider.getPluginId());

        // check that the device doesn't already exist
        if (hasTriggerProvider(userId, hubId, provider.getPluginId(), provider.getId())) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate trigger provider: " + provider.getId());
        }

        if (context != null) {
            // register provider as a service
            Properties props = new Properties();
            props.setProperty("pluginId", provider.getPluginId());
            props.setProperty("providerId", provider.getId());
            ServiceRegistration providerReg = context.registerService(
                    TriggerProvider.class.getName(),
                    provider,
                    props
            );
            // TODO: store service registration for later

            logger.debug("Trigger provider {} registered", provider.getId());
        }
    }

    @Override
    public boolean hasTriggerProvider(String userId, String hubId, String pluginId, String providerId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences(null, "(&(objectClass=" + TriggerProvider.class.getName() + ")(pluginId=" + pluginId + ")(providerId=" + providerId + "))");
            if (references != null && references.length == 1) {
                return true;
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate trigger providers detected");
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving trigger provider", e);
        }
        return false;
    }

    public Collection<HobsonTrigger> getAllTriggers(String userId, String hubId) {
        List<HobsonTrigger> triggers = new ArrayList<HobsonTrigger>();
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(TriggerProvider.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    TriggerProvider provider = (TriggerProvider)bundleContext.getService(ref);
                    triggers.addAll(provider.getTriggers());
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new TriggerException("Error obtaining trigger providers", e);
        }
        return triggers;
    }

    public HobsonTrigger getTrigger(String userId, String hubId, String providerId, String triggerId) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TriggerProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TriggerProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TriggerProvider provider = (TriggerProvider)bundleContext.getService(refs[0]);
                return provider.getTrigger(triggerId);
            } else {
                throw new TriggerException("Unable to find unique trigger provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TriggerException("Error obtaining trigger providers", e);
        }
    }

    public void addTrigger(String userId, String hubId, String providerId, Object trigger) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TriggerProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TriggerProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TriggerProvider provider = (TriggerProvider)bundleContext.getService(refs[0]);
                provider.addTrigger(trigger);
            } else {
                throw new TriggerException("Unable to find unique trigger provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TriggerException("Error obtaining trigger providers", e);
        }
    }

    @Override
    public void deleteTrigger(String userId, String hubId, String providerId, String triggerId) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TriggerProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TriggerProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TriggerProvider provider = (TriggerProvider)bundleContext.getService(refs[0]);
                provider.deleteTrigger(triggerId);
            } else {
                throw new TriggerException("Unable to find unique trigger provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TriggerException("Error obtaining trigger providers", e);
        }
    }
}
