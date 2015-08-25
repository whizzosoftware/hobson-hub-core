package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.MockTaskManager;
import com.whizzosoftware.hobson.api.task.TaskContext;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskConditionProcessorTest {
    @Test
    public void testEvaluateWithMissingConditionClass() {
        // create task
        TaskContext ctx = TaskContext.createLocal("task1");
        PropertyContainerSet conditionSet = new PropertyContainerSet();
        List<PropertyContainer> cprops = new ArrayList<>();
        Map<String,Object> propValues = new HashMap<>();
        cprops.add(new PropertyContainer(null, propValues));
        conditionSet.setProperties(cprops);
        PropertyContainerSet actionSet = new PropertyContainerSet();

//        HobsonTask task = new HobsonTask(ctx, "name", "desc", null, conditionSet, actionSet);
//        MockTaskManager taskManager = new MockTaskManager();
//        taskManager.publishTask(task);
//
//        // create processor and evaluate
//        TaskConditionProcessor p = new TaskConditionProcessor();
//        try {
//            p.evaluate(taskManager, ctx);
//            fail("Should have thrown exception");
//        } catch (HobsonRuntimeException ignored) {}
    }

    @Test
    public void testEvaluateWithNoConditions() {
        PluginContext pctx = PluginContext.createLocal("plugin1");

        // create task
        TaskContext ctx = TaskContext.createLocal("task1");
        PropertyContainerSet conditionSet = new PropertyContainerSet();
        PropertyContainerSet actionSet = new PropertyContainerSet("actionset1");

        MockTaskManager taskManager = new MockTaskManager();

//        HobsonTask task = new HobsonTask(ctx, "name", "desc", null, conditionSet, actionSet);
//        taskManager.publishTask(task);
//
//        TaskConditionProcessor p = new TaskConditionProcessor();
//        p.evaluate(taskManager, ctx);
//
//        assertEquals(1, taskManager.getActionSetExecutions().size());
//        assertEquals("actionset1", taskManager.getActionSetExecutions().get(0));
    }

    @Test
    public void testEvaluateWithSuccessfulCondition() {
        PluginContext pctx = PluginContext.createLocal("plugin1");
        MockTaskConditionClass mtcc = new MockTaskConditionClass(pctx);

        // create task
        TaskContext ctx = TaskContext.createLocal("task1");
        PropertyContainerSet conditionSet = new PropertyContainerSet();
        List<PropertyContainer> cprops = new ArrayList<>();
        Map<String,Object> propValues = new HashMap<>();
        propValues.put("result", true);
        cprops.add(new PropertyContainer(mtcc.getContext(), propValues));
        conditionSet.setProperties(cprops);

        PropertyContainerSet actionSet = new PropertyContainerSet("actionset1");

        MockTaskManager taskManager = new MockTaskManager();

        taskManager.publishConditionClass(mtcc);

//        HobsonTask task = new HobsonTask(ctx, "name", "desc", null, conditionSet, actionSet);
//        taskManager.publishTask(task);
//
//        TaskConditionProcessor p = new TaskConditionProcessor();
//        p.evaluate(taskManager, ctx);
//
//        assertEquals(1, taskManager.getActionSetExecutions().size());
//        assertEquals("actionset1", taskManager.getActionSetExecutions().get(0));
    }

    @Test
    public void testEvaluateWithFailedCondition() {
        PluginContext pctx = PluginContext.createLocal("plugin1");
        MockTaskConditionClass mtcc = new MockTaskConditionClass(pctx);

        // create task
        TaskContext ctx = TaskContext.createLocal("task1");
        PropertyContainerSet conditionSet = new PropertyContainerSet();
        List<PropertyContainer> cprops = new ArrayList<>();
        Map<String,Object> propValues = new HashMap<>();
        propValues.put("result", false);
        cprops.add(new PropertyContainer(mtcc.getContext(), propValues));
        conditionSet.setProperties(cprops);

        PropertyContainerSet actionSet = new PropertyContainerSet();

        MockTaskManager taskManager = new MockTaskManager();

        taskManager.publishConditionClass(mtcc);

//        HobsonTask task = new HobsonTask(ctx, "name", "desc", null, conditionSet, actionSet);
//        taskManager.publishTask(task);
//
//        TaskConditionProcessor p = new TaskConditionProcessor();
//        p.evaluate(taskManager, ctx);
//
//        assertEquals(0, taskManager.getActionSetExecutions().size());
    }
}
