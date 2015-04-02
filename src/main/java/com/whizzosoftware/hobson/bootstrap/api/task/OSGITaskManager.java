/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.task.*;
import com.whizzosoftware.hobson.bootstrap.api.util.BundleUtil;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * An OSGi implementation of TaskManager.
 *
 * @author Dan Noguerol
 */
public class OSGITaskManager implements TaskManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile BundleContext bundleContext;
    private volatile ExecutorService executorService;

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();

    @Override
    public void publishTaskProvider(String userId, String hubId, TaskProvider provider) {
        BundleContext context = BundleUtil.getBundleContext(getClass(), provider.getPluginId());

        // check that the device doesn't already exist
        if (hasTaskProvider(userId, hubId, provider.getPluginId(), provider.getId())) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate task provider: " + provider.getId());
        }

        if (context != null) {
            // register provider as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", provider.getPluginId());
            props.put("providerId", provider.getId());

            synchronized (serviceRegistrationMap) {
                List<ServiceRegistration> regList = serviceRegistrationMap.get(provider.getPluginId());
                if (regList == null) {
                    regList = new ArrayList<>();
                    serviceRegistrationMap.put(provider.getPluginId(), regList);
                }
                regList.add(
                    context.registerService(
                            TaskProvider.class.getName(),
                            provider,
                            props
                    )
                );
            }


            logger.debug("Task provider {} registered", provider.getId());
        }
    }

    @Override
    public void unpublishAllTaskProviders(String userId, String hubId, String pluginId) {
        synchronized (serviceRegistrationMap) {
            List<ServiceRegistration> srl = serviceRegistrationMap.get(pluginId);
            if (srl != null) {
                for (ServiceRegistration sr : srl) {
                    sr.unregister();
                }
                serviceRegistrationMap.remove(pluginId);
            }
        }
    }

    @Override
    public boolean hasTaskProvider(String userId, String hubId, String pluginId, String providerId) {
        try {
            BundleContext context = BundleUtil.getBundleContext(getClass(), null);
            ServiceReference[] references = context.getServiceReferences((String)null, "(&(objectClass=" + TaskProvider.class.getName() + ")(pluginId=" + pluginId + ")(providerId=" + providerId + "))");
            if (references != null && references.length == 1) {
                return true;
            } else if (references != null && references.length > 1) {
                throw new HobsonRuntimeException("Duplicate task providers detected");
            }
        } catch (InvalidSyntaxException e) {
            throw new HobsonRuntimeException("Error retrieving task provider", e);
        }
        return false;
    }

    @Override
    public Collection<HobsonTask> getAllTasks(String userId, String hubId) {
        List<HobsonTask> tasks = new ArrayList<HobsonTask>();
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    TaskProvider provider = (TaskProvider)bundleContext.getService(ref);
                    tasks.addAll(provider.getTasks());
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
        return tasks;
    }

    @Override
    public HobsonTask getTask(String userId, String hubId, String providerId, String taskId) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TaskProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TaskProvider provider = (TaskProvider)bundleContext.getService(refs[0]);
                return provider.getTask(taskId);
            } else {
                throw new TaskException("Unable to find unique task provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
    }

    @Override
    public void executeTask(String userId, String hubId, String providerId, String taskId) {
        final HobsonTask task = getTask(userId, hubId, providerId, taskId);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                task.execute();

            }
        });
    }

    @Override
    public void addTask(String userId, String hubId, String providerId, Object task) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TaskProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TaskProvider provider = (TaskProvider)bundleContext.getService(refs[0]);
                provider.addTask(task);
            } else {
                throw new TaskException("Unable to find unique task provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
    }

    @Override
    public void updateTask(String userId, String hubId, String providerId, String taskId, Object task) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TaskProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TaskProvider provider = (TaskProvider)bundleContext.getService(refs[0]);
                provider.updateTask(taskId, task);
            } else {
                throw new TaskException("Unable to find unique task provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
    }

    @Override
    public void deleteTask(String userId, String hubId, String providerId, String taskId) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + TaskProvider.class.getName() + ")(providerId=" + providerId + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                TaskProvider provider = (TaskProvider)bundleContext.getService(refs[0]);
                provider.deleteTask(taskId);
            } else {
                throw new TaskException("Unable to find unique task provider for id: " + providerId);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
    }
}
