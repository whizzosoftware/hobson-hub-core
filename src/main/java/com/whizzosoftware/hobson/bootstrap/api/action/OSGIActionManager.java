/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonNotFoundException;
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.*;
import com.whizzosoftware.hobson.api.action.store.ActionStore;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.action.job.AsyncJobHandle;
import com.whizzosoftware.hobson.api.action.job.Job;
import com.whizzosoftware.hobson.api.action.job.JobInfo;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.property.*;
import com.whizzosoftware.hobson.bootstrap.api.action.store.MapDBActionStore;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import io.netty.util.concurrent.Future;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class OSGIActionManager implements ActionManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final long DEFAULT_TIMEOUT = 2000;

    @Inject
    volatile private BundleContext bundleContext;
    @Inject
    volatile private DeviceManager deviceManager;
    @Inject
    volatile private PluginManager pluginManager;

    private ActionStore actionStore;
    private final Map<String,Job> jobMap = Collections.synchronizedMap(new HashMap<String,Job>());
    private int maxJobCount = Integer.parseInt(System.getProperty("maxJobCount", "100"));

    public void setMaxJobCount(int maxJobCount) {
        this.maxJobCount = maxJobCount;
    }

    public void start() {
        // if a task store hasn't already been injected, create a default one
        if (actionStore == null) {
            this.actionStore = new MapDBActionStore(
                    pluginManager.getDataFile(
                        PluginContext.createLocal(FrameworkUtil.getBundle(getClass()).getSymbolicName()),
                        "actions"
                    )
            );
        }
    }

    @Override
    public void addJobStatusMessage(PluginContext ctx, String msgName, Object o) {
        for (Job job : jobMap.values()) {
            if (job.isAssociatedWithPlugin(ctx)) {
                job.message(msgName, o);
            }
        }
    }

    @Override
    public AsyncJobHandle executeAction(PropertyContainer action) {
        ActionClass ac = getActionClass(action.getContainerClassContext());

        // make sure action properties are valid
        ac.validate(action);

        // instantiate action
        Action a = ((ActionProvider)ac).createAction(action.getPropertyValues());

        if (a != null) {
            // create job
            Job j = createJob(a);
            jobMap.put(j.getId(), j);

            return new AsyncJobHandle(j.getId(), j.start());
        } else {
            throw new HobsonRuntimeException("Unable to create action with action class: " + ac.getContext());
        }
    }

    @Override
    public AsyncJobHandle executeActionSet(PropertyContainerSet actionSet) {
        // instantiate actions
        List<Action> actions = new ArrayList<>();
        for (PropertyContainer action : actionSet.getProperties()) {
            ActionClass ac = getActionClass(action.getContainerClassContext());
            // make sure action properties are valid
            ac.validate(action);
            // add to the list
            actions.add(((ActionProvider)ac).createAction(action.getPropertyValues()));
        }

        // create composite action
        CompositeAction action = new CompositeAction(actions);

        // create job
        Job j = createJob(action);
        return new AsyncJobHandle(j.getId(), j.start());
    }

    @Override
    public ActionClass getActionClass(PropertyContainerClassContext ctx) {
        try {
            String s = "(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass)(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + ")";
            if (ctx.hasDeviceContext()) {
                s += "(deviceId=" + ctx.getDeviceId() + "))";
            } else {
                s += ")";
            }
            Filter filter = bundleContext.createFilter(s);
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return ((ActionClass)bundleContext.getService(refs[0]));
            } else {
                throw new HobsonRuntimeException("Unable to find action class: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public Collection<ActionClass> getActionClasses(PluginContext ctx) {
        List<ActionClass> results = new ArrayList<>();
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass)(pluginId=" + ctx.getPluginId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    ActionClass ac = (ActionClass)bundleContext.getService(ref);
                    // in this case, we only want action classes that have the correct plugin identifier but no device identifier
                    if (!ac.getContext().hasDeviceContext()) {
                        results.add(((ActionClass) bundleContext.getService(ref)));
                    }
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action class: " + ctx, e);
        }
    }

    @Override
    public Collection<ActionClass> getActionClasses(HubContext ctx, boolean applyConstraints) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass))");
            return getActionClasses(ctx, filter, applyConstraints);
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    @Override
    public Collection<ActionClass> getActionClasses(DeviceContext ctx, boolean applyConstraints) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(type=actionClass)(pluginId=" + ctx.getPluginId() + ")(deviceId=" + ctx.getDeviceId() + "))");
            return getActionClasses(ctx.getHubContext(), filter, applyConstraints);
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    private Collection<ActionClass> getActionClasses(HubContext ctx, Filter filter, boolean applyConstraints) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            List<ActionClass> results = new ArrayList<>();
            ServiceReference[] references = context.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            if (references != null) {
                Collection<String> publishedVariableNames = deviceManager.getDeviceVariableNames(ctx);
                for (ServiceReference ref : references) {
                    PropertyContainerClass pcc = (PropertyContainerClass)context.getService(ref);
                    if (!applyConstraints || pcc.evaluatePropertyConstraints(publishedVariableNames)) {
                        results.add(((ActionClass)context.getService(ref)));
                    }
                }
            }
            return results;
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving action classes", e);
        }
    }

    @Override
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId) {
        return actionStore.getActionSet(ctx, actionSetId);
    }

    @Override
    public Collection<PropertyContainerSet> getActionSets(HubContext ctx) {
        return actionStore.getAllActionSets(ctx);
    }

    @Override
    public JobInfo getJobInfo(HubContext ctx, String jobId) {
        JobInfo info = jobMap.get(jobId);
        if (info != null) {
            return info;
        } else {
            throw new HobsonNotFoundException("Job \"" + jobId + "\" not found");
        }
    }

    @Override
    public boolean hasActionClass(PropertyContainerClassContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + PropertyContainerClass.class.getName() + ")(pluginId=" + ctx.getPluginContext().getPluginId() + ")(classId=" + ctx.getContainerClassId() + ")(type=actionClass))");
            ServiceReference[] refs = bundleContext.getServiceReferences(PropertyContainerClass.class.getName(), filter.toString());
            return (refs != null && refs.length == 1);
        } catch (InvalidSyntaxException e) {
            logger.error("Error trying to check resolved task");
        }
        return false;
    }

    @Override
    public synchronized void publishActionProvider(ActionProvider actionProvider) {
        String pluginId = actionProvider.getContext().getPluginId();
        String deviceId = actionProvider.getContext().getDeviceId();
        BundleContext ctx = BundleUtil.getBundleContext(getClass(), pluginId);

        if (ctx != null) {
            if (pluginId == null) {
                throw new HobsonRuntimeException("Unable to publish action with null plugin ID");
            }

            Bundle b = BundleUtil.getBundleForSymbolicName(actionProvider.getContext().getPluginContext().getPluginId());
            BundleContext bc = b != null ? b.getBundleContext() : bundleContext;

            // register action class as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            if (deviceId != null) {
                props.put("deviceId", deviceId);
            }
            props.put("classId", actionProvider.getContext().getContainerClassId());
            props.put("type", "actionClass");
            bc.registerService(PropertyContainerClass.class, actionProvider, props);
        } else {
            throw new HobsonRuntimeException("Unable to obtain context to publish action");
        }
    }

    @Override
    public PropertyContainerSet publishActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
        return actionStore.saveActionSet(ctx, name, actions);
    }

    @Override
    public Future stopJob(HubContext ctx, String jobId) {
        Job job = jobMap.get(jobId);
        if (job != null) {
            return job.stop();
        } else {
            throw new HobsonNotFoundException("Unable to find job \"" + jobId + "\"");
        }
    }

    int getJobCount() {
        return jobMap.size();
    }

    private Job createJob(Action a) {
        return createJob(a, System.currentTimeMillis());
    }

    synchronized Job createJob(Action a, long now) {
        // if there's no free slot for the new job, remove all completed jobs
        if (jobMap.size() >= maxJobCount) {
            logger.debug("Max job count has been reached; puring completed jobs");
            Iterator<Map.Entry<String,Job>> it = jobMap.entrySet().iterator();
            int count = 0;
            while (it.hasNext()) {
                Job j = it.next().getValue();
                if (j.isComplete()) {
                    it.remove();
                    count++;
                }
            }
            logger.debug("Successfully purged {} jobs", count);
            // if there's still no free slot, remove all incomplete jobs that started more than an hour ago
            if (jobMap.size() >= maxJobCount) {
                count = 0;
                logger.debug("Purging completed jobs didn't free up enough slots; stop incomplete jobs more than an hour old");
                it = jobMap.entrySet().iterator();
                while (it.hasNext()) {
                    Job j = it.next().getValue();
                    if (j.isOlderThanAnHour(now)) {
                        j.stop();
                        it.remove();
                        count++;
                    }
                }
                logger.debug("Successfully purged {} jobs", count);
                // if there's still not enough space, throw an exception
                if (jobMap.size() >= maxJobCount) {
                    logger.error("There are more than {} active jobs less than an hour old; a new job cannot be created", maxJobCount);
                    throw new HobsonRuntimeException("Unable to create a new job due to too many recent active jobs");
                }
            }
        }

        // create job
        Job job = new Job(a, DEFAULT_TIMEOUT, now);
        jobMap.put(job.getId(), job);
        return job;
    }


}
