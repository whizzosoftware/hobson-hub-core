/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task.store;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.MockTaskManager;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.condition.ConditionClassType;
import com.whizzosoftware.hobson.api.task.condition.ConditionEvaluationContext;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MapDBTaskStoreTest {
    @Test
    public void testAddActionSet() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MockTaskManager taskManager = new MockTaskManager();

        MapDBTaskStore store = new MapDBTaskStore(dbFile, taskManager);

        List<PropertyContainer> actions = new ArrayList<>();
        actions.add(
            new PropertyContainer(
                PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1"),
                Collections.singletonMap("foo", (Object) "bar")
            )
        );
        String actionSetId = store.addActionSet(HubContext.createLocal(), "actionSet1", actions).getId();

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBTaskStore(dbFile, taskManager);

        PropertyContainerSet pcs = store.getActionSet(HubContext.createLocal(), actionSetId);
        assertEquals(actionSetId, pcs.getId());
        assertTrue(pcs.hasProperties());
        assertEquals(1, pcs.getProperties().size());
        assertEquals("cc1", pcs.getProperties().get(0).getContainerClassContext().getContainerClassId());
        assertEquals("plugin1", pcs.getProperties().get(0).getContainerClassContext().getPluginId());
        assertTrue(pcs.getProperties().get(0).hasPropertyValues());
        assertEquals("bar", pcs.getProperties().get(0).getPropertyValue("foo"));
    }

    @Test
    public void testAddTask() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MockTaskManager taskManager = new MockTaskManager();
        MapDBTaskStore store = new MapDBTaskStore(dbFile, taskManager);

        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer(PropertyContainerClassContext.create(HubContext.createLocal(), "cc1"), Collections.singletonMap("foo", (Object) "value")));
        TaskContext tctx = TaskContext.create(HubContext.createLocal(), "task1");
        HobsonTask task = new HobsonTask(
            tctx,
            "My Task",
            "Do something",
            null,
            conditions,
            new PropertyContainerSet("actionSet1")
        );
        store.addTask(task);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBTaskStore(dbFile, taskManager);

        HobsonTask t = store.getTask(tctx);
        assertNotNull(t);
        assertEquals("My Task", t.getName());
        assertEquals("Do something", t.getDescription());
        assertNull(t.getProperties());
        assertNotNull(t.getConditions());
        assertNotNull(t.getActionSet());
    }

    @Test
    public void testAddTaskWithDeviceContext() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MockTaskManager taskManager = new MockTaskManager();
        MapDBTaskStore store = new MapDBTaskStore(dbFile, taskManager);

        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer(PropertyContainerClassContext.create(HubContext.createLocal(), "turnOn"), Collections.singletonMap("devices", (Object)Collections.singletonList(DeviceContext.createLocal("plugin1", "device1")))));
        TaskContext tctx = TaskContext.create(HubContext.createLocal(), "task1");
        HobsonTask task = new HobsonTask(
                tctx,
                "My Task 1",
                "Do something 1",
                null,
                conditions,
                new PropertyContainerSet("actionSet1")
        );
        store.addTask(task);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBTaskStore(dbFile, taskManager);

        HobsonTask t = store.getTask(tctx);
        assertNotNull(t);
        assertEquals("My Task 1", t.getName());
        assertEquals("Do something 1", t.getDescription());
        assertNull(t.getProperties());
        assertNotNull(t.getConditions());
        assertEquals(1, t.getConditions().size());
        assertTrue(t.getConditions().get(0).getPropertyValue("devices") instanceof List);
        assertTrue(((List)t.getConditions().get(0).getPropertyValue("devices")).get(0) instanceof DeviceContext);
        assertEquals("plugin1", ((DeviceContext) ((List) t.getConditions().get(0).getPropertyValue("devices")).get(0)).getPluginId());
        assertEquals("device1", ((DeviceContext) ((List) t.getConditions().get(0).getPropertyValue("devices")).get(0)).getDeviceId());
        assertNotNull(t.getActionSet());
    }

    @Test
    public void testGetAllTasks() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MockTaskManager taskManager = new MockTaskManager();
        MapDBTaskStore store = new MapDBTaskStore(dbFile, taskManager);

        // add an action set
        List<PropertyContainer> actions = new ArrayList<>();
        actions.add(
                new PropertyContainer(
                        PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1"),
                        Collections.singletonMap("foo", (Object) "bar")
                )
        );
        store.addActionSet(HubContext.createLocal(), "actionSet1", actions).getId();

        // add a task
        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer("condition1", PropertyContainerClassContext.create(HubContext.createLocal(), "cc1"), Collections.singletonMap("foo", (Object) "value")));
        TaskContext tctx = TaskContext.create(HubContext.createLocal(), "task1");
        HobsonTask task = new HobsonTask(
                tctx,
                "My Task",
                "Do something",
                null,
                conditions,
                new PropertyContainerSet("actionSet1")
        );
        store.addTask(task);

        // close and re-open the store to make sure we're starting from scratch
        store.close();
        store = new MapDBTaskStore(dbFile, taskManager);

        // make sure only 1 task comes back
        Collection<HobsonTask> tasks = store.getAllTasks();
        assertEquals(1, tasks.size());
    }

    @Test
    public void testGetAllTasksForPlugin() throws Exception {
        File dbFile = File.createTempFile("test", ".mapdb");
        dbFile.deleteOnExit();

        MockTaskManager taskManager = new MockTaskManager();
        MapDBTaskStore store = new MapDBTaskStore(dbFile, taskManager);

        PropertyContainerClassContext pccc1 = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1");
        PropertyContainerClassContext pccc2 = PropertyContainerClassContext.create(PluginContext.createLocal("plugin2"), "cc2");
        taskManager.publishConditionClass(new TaskConditionClass(pccc1, "", "") {
            @Override
            public ConditionClassType getConditionClassType() {
                return ConditionClassType.trigger;
            }

            @Override
            public List<TypedProperty> createProperties() {
                return null;
            }

            @Override
            public boolean evaluate(ConditionEvaluationContext context, PropertyContainer values) {
                return true;
            }
        });
        taskManager.publishConditionClass(new TaskConditionClass(pccc2, "", "") {
            @Override
            public ConditionClassType getConditionClassType() {
                return ConditionClassType.trigger;
            }

            @Override
            public List<TypedProperty> createProperties() {
                return null;
            }

            @Override
            public boolean evaluate(ConditionEvaluationContext context, PropertyContainer values) {
                return true;
            }
        });

        // add task 1
        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer("condition1", pccc1, Collections.singletonMap("foo", (Object) "value")));
        TaskContext tctx = TaskContext.create(HubContext.createLocal(), "task1");
        HobsonTask task = new HobsonTask(
                tctx,
                "My Task",
                "Do something",
                null,
                conditions,
                new PropertyContainerSet("actionSet1")
        );
        store.addTask(task);

        // add task 2
        conditions = new ArrayList<>();
        conditions.add(new PropertyContainer("condition1", pccc2, Collections.singletonMap("foo", (Object) "value")));
        tctx = TaskContext.create(HubContext.createLocal(), "task2");
        task = new HobsonTask(
                tctx,
                "My Task",
                "Do something",
                null,
                conditions,
                new PropertyContainerSet("actionSet1")
        );
        store.addTask(task);

        // make sure only 1 task comes back
        Collection<HobsonTask> tasks = store.getAllTasks(taskManager, PluginContext.createLocal("plugin1"));
        assertEquals(1, tasks.size());

        // close store and create new one against same file
        store.close();
        store = new MapDBTaskStore(dbFile, taskManager);

        // make sure we can still retrieve tasks
        tasks = store.getAllTasks(taskManager, PluginContext.createLocal("plugin1"));
        assertEquals(1, tasks.size());
        task = tasks.iterator().next();
        assertEquals("My Task", task.getName());
        assertEquals("Do something", task.getDescription());
    }
}
