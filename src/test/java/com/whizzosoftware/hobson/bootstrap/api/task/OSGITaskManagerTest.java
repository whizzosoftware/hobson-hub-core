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

import com.whizzosoftware.hobson.api.HobsonInvalidRequestException;
import com.whizzosoftware.hobson.api.action.MockActionManager;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.event.task.TaskRegistrationEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.MockTaskManager;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.condition.ConditionClassType;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClass;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClassProvider;
import com.whizzosoftware.hobson.bootstrap.api.task.store.MapDBTaskStore;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

public class OSGITaskManagerTest {
    @Test
    public void testTaskStartup() {
        MockTaskStore store = new MockTaskStore();

        List<PropertyContainer> taskConditions = new ArrayList<>();
        final PropertyContainerClassContext pccc = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1");
        taskConditions.add(new PropertyContainer(pccc, null));

        final HobsonTask task = new HobsonTask(TaskContext.createLocal("task1"), "task", null, true, null, taskConditions, null);
        store.saveTask(task);

        MockPluginManager pm = new MockPluginManager();
        final MockHobsonPlugin plugin = new MockHobsonPlugin("plugin1", "1.0.0", "");
        final MockTaskProvider taskProvider = new MockTaskProvider();
        plugin.setTaskProvider(new MockTaskProvider());
        plugin.setTaskManager(new MockTaskManager());
        plugin.setActionManager(new MockActionManager());
        pm.addLocalPlugin(plugin);

        MockEventManager em = new MockEventManager();
        OSGITaskManager tm = new OSGITaskManager();
        tm.setPluginManager(pm);
        tm.setEventManager(em);
        tm.setTaskStore(store);
        tm.setTaskRegistrationContext(new TaskRegistrationContext() {
            @Override
            public Collection<HobsonTask> getTasks(HubContext ctx) {
                return Collections.singletonList(task);
            }

            @Override
            public boolean isTaskFullyResolved(HobsonTask task) {
                return true;
            }

            @Override
            public HobsonLocalPluginDescriptor getPluginForTask(HobsonTask task) {
                return plugin.getDescriptor();
            }
        });
        assertEquals(0, em.getEventCount());
        tm.start();

        // the task creation event is async so we need to wait until the callback is completed
        synchronized (taskProvider) {
            try {
                tm.queueTaskRegistration();
                taskProvider.wait(2000);
            } catch (InterruptedException ignored) {}
        }

        assertEquals(1, em.getEventCount());
        assertTrue(em.getEvent(0) instanceof TaskRegistrationEvent);
    }

    @Test
    public void testCreateTask() throws Exception {
        final PluginContext pctx = PluginContext.createLocal("plugin1");
        File f = File.createTempFile("foo", "db");
        f.deleteOnExit();
        MapDBTaskStore store = new MapDBTaskStore(f);
        MockActionManager am = new MockActionManager();
        MockEventManager em = new MockEventManager();
        MockPluginManager pm = new MockPluginManager();
        OSGITaskManager tm = new OSGITaskManager();
        tm.setActionManager(am);
        tm.setPluginManager(pm);
        tm.setTaskStore(store);
        tm.setTaskRegistrationExecutor(new TaskRegistrationExecutor(HubContext.createLocal(), em, null));
        tm.setTaskConditionClassProvider(new TaskConditionClassProvider() {
            @Override
            public TaskConditionClass getConditionClass(PropertyContainerClassContext ctx) {
                final List<TypedProperty> props = new ArrayList<>();
                props.add(new TypedProperty.Builder("id1", "name1", "desc1", TypedProperty.Type.STRING).build());
                return new MockTaskConditionClass(pctx, ConditionClassType.trigger) {
                    public List<TypedProperty> createProperties() {
                        return props;
                    }
                };
            }
        });

        List<PropertyContainer> conds = new ArrayList<>();
        Map<String,Object> map = new HashMap<>();
        map.put("id1", "bar");
        conds.add(new PropertyContainer(PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1"), map));

        List<PropertyContainer> lpc = new ArrayList<>();
        lpc.add(new PropertyContainer(PropertyContainerClassContext.create(DeviceContext.createLocal("plugin1", "device1"), "cc1"), Collections.singletonMap("foo", (Object)"bar")));
        lpc.add(new PropertyContainer(PropertyContainerClassContext.create(DeviceContext.createLocal("plugin1", "device1"), "cc2"), Collections.singletonMap("bar", (Object)"foo")));
        PropertyContainerSet actions = new PropertyContainerSet(null, lpc);

        try {
            tm.createTask(HubContext.createLocal(), null, null, null, null);
            fail("Should have thrown exception");
        } catch (HobsonInvalidRequestException ignored) {}
        try {
            tm.createTask(HubContext.createLocal(), "name", null, null, null);
            fail("Should have thrown exception");
        } catch (HobsonInvalidRequestException ignored) {}
        try {
            tm.createTask(HubContext.createLocal(), "name", "desc", null, null);
            fail("Should have thrown exception");
        } catch (HobsonInvalidRequestException ignored) {}
        tm.createTask(HubContext.createLocal(), "name", "desc", conds, actions);

        Collection<HobsonTask> tasks = tm.getTasks(HubContext.createLocal());
        assertEquals(1, tasks.size());
        HobsonTask task = tasks.iterator().next();
        assertEquals("name", task.getName());
        assertEquals("desc", task.getDescription());
        List<PropertyContainer> pcs = task.getConditions();
        assertEquals(1, pcs.size());
        PropertyContainer pc = pcs.get(0);
        assertEquals("bar", pc.getStringPropertyValue("id1"));
        assertNotNull(task.getActionSet());
        assertTrue(task.getActionSet().hasProperties());
        assertEquals(2, task.getActionSet().getProperties().size());
    }
}
