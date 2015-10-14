package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class OSGITaskManagerTest {
    @Test
    public void testTaskStartup() {
        MockTaskStore store = new MockTaskStore();

        List<PropertyContainer> taskConditions = new ArrayList<>();
        final PropertyContainerClassContext pccc = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1");
        taskConditions.add(new PropertyContainer(pccc, null));

        final HobsonTask task = new HobsonTask(TaskContext.createLocal("task1"), "task", null, null, taskConditions, null);
        store.saveTask(task);

        MockPluginManager pm = new MockPluginManager();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1");
        MockTaskProvider taskProvider = new MockTaskProvider();
        plugin.setTaskProvider(taskProvider);
        pm.addLocalPlugin(plugin);

        OSGITaskManager tm = new OSGITaskManager();
        tm.setPluginManager(pm);
        tm.setTaskStore(store);
        tm.setTaskRegistrationContext(new TaskRegistrationContext() {
            @Override
            public Collection<HobsonTask> getAllTasks(HubContext ctx) {
                return Collections.singletonList(task);
            }

            @Override
            public boolean isTaskFullyResolved(HobsonTask task) {
                return true;
            }

            @Override
            public HobsonPlugin getPluginForTask(HobsonTask task) {
                return plugin;
            }
        });
        tm.start();

        // the task creation event is async so we need to wait until the callback is completed
        synchronized (taskProvider) {
            try {
                tm.queueTaskRegistration();
                taskProvider.wait();
            } catch (InterruptedException ignored) {}
        }

        assertEquals(1, taskProvider.getCreatedTasks().size());
        assertEquals(task, taskProvider.getCreatedTasks().get(0));
    }
}
