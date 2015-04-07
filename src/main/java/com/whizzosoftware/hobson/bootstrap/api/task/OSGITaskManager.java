/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.HobsonPlugin;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
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
    private volatile PluginManager pluginManager;

    private final Map<String,List<ServiceRegistration>> serviceRegistrationMap = new HashMap<>();

    @Override
    public void publishTask(HobsonTask task) {
        String pluginId = task.getContext().getPluginId();
        BundleContext context = BundleUtil.getBundleContext(getClass(), pluginId);

        // check that the task doesn't already exist
        if (getTask(task.getContext()) != null) {
            throw new HobsonRuntimeException("Attempt to publish a duplicate task: " + task.getContext());
        }

        if (context != null) {
            // register task as a service
            Dictionary<String,String> props = new Hashtable<>();
            props.put("pluginId", pluginId);
            props.put("id", task.getContext().getTaskId());

            synchronized (serviceRegistrationMap) {
                List<ServiceRegistration> regList = serviceRegistrationMap.get(pluginId);
                if (regList == null) {
                    regList = new ArrayList<>();
                    serviceRegistrationMap.put(pluginId, regList);
                }
                regList.add(
                    context.registerService(
                            HobsonTask.class.getName(),
                            task,
                            props
                    )
                );
            }

            logger.debug("Task {} registered", task.getContext());
        }
    }

    @Override
    public void unpublishAllTasks(PluginContext ctx) {
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
    public void createTask(PluginContext ctx, final Object config) {
        final HobsonPlugin plugin = pluginManager.getPlugin(ctx);
        if (plugin != null) {
            final TaskProvider provider = getTaskProvider(plugin);
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    provider.onCreateTask(config);
                }
            });
        } else {
            throw new TaskException("No plugin found: " + ctx);
        }
    }

    @Override
    public Collection<HobsonTask> getAllTasks(HubContext ctx) {
        List<HobsonTask> tasks = new ArrayList<HobsonTask>();
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(HobsonTask.class.getName(), null);
            if (refs != null) {
                for (ServiceReference ref : refs) {
                    tasks.add((HobsonTask) bundleContext.getService(ref));
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task providers", e);
        }
        return tasks;
    }

    @Override
    public HobsonTask getTask(TaskContext ctx) {
        try {
            Filter filter = bundleContext.createFilter("(&(objectClass=" + HobsonTask.class.getName() + ")(pluginId=" + ctx.getPluginId() + ")(id=" + ctx.getTaskId() + "))");
            ServiceReference[] refs = bundleContext.getServiceReferences(TaskProvider.class.getName(), filter.toString());
            if (refs != null && refs.length == 1) {
                return (HobsonTask)bundleContext.getService(refs[0]);
            } else {
                throw new TaskException("Unable to find unique task for id: " + ctx);
            }
        } catch (InvalidSyntaxException e) {
            throw new TaskException("Error obtaining task", e);
        }
    }

    @Override
    public void executeTask(TaskContext ctx) {
        final HobsonTask task = getTask(ctx);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                task.execute();

            }
        });
    }

    @Override
    public void updateTask(TaskContext ctx, final Object config) {
        final HobsonPlugin plugin = pluginManager.getPlugin(ctx.getPluginContext());
        if (plugin != null) {
            final TaskProvider provider = getTaskProvider(plugin);
            final HobsonTask task = getTask(ctx);
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    provider.onUpdateTask(task, config);
                }
            });
        } else {
            throw new TaskException("No plugin found: " + ctx);
        }
    }

    @Override
    public void deleteTask(final TaskContext ctx) {
        final HobsonPlugin plugin = pluginManager.getPlugin(ctx.getPluginContext());
        if (plugin != null) {
            final TaskProvider provider = getTaskProvider(plugin);
            final HobsonTask task = getTask(ctx);
            plugin.getRuntime().getEventLoopExecutor().executeInEventLoop(new Runnable() {
                @Override
                public void run() {
                    // delete the task from the provider
                    provider.onDeleteTask(task);

                    // unpublish the task service
                    List<ServiceRegistration> regs = serviceRegistrationMap.get(ctx.getPluginId());
                    if (regs != null) {
                        for (ServiceRegistration sr : regs) {
                            // TODO
                        }
                    }
                }
            });
        } else {
            throw new TaskException("No plugin found: " + ctx);
        }
    }

    private TaskProvider getTaskProvider(PluginContext ctx) {
        HobsonPlugin plugin = pluginManager.getPlugin(ctx);
        if (plugin != null) {
            return getTaskProvider(plugin);
        } else {
            throw new TaskException("No plugin found for task " + ctx);
        }
    }

    private TaskProvider getTaskProvider(HobsonPlugin plugin) {
        if (plugin.getRuntime() != null) {
            if (plugin.getRuntime().getTaskProvider() != null) {
                return plugin.getRuntime().getTaskProvider();
            } else {
                throw new TaskException("No task provider available for task " + plugin.getContext());
            }
        } else {
            throw new TaskException("No plugin runtime available for task " + plugin.getContext());
        }
    }
}
