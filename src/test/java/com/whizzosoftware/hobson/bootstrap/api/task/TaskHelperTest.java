package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.task.MockTaskManager;
import com.whizzosoftware.hobson.api.task.TaskHelper;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.condition.ConditionClassType;
import com.whizzosoftware.hobson.api.task.condition.ConditionEvaluationContext;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TaskHelperTest {
    @Test
    public void testGetTriggerConditionWithNoConditions() {
        TaskManager tm = new MockTaskManager();
        List<PropertyContainer> conditions = new ArrayList<>();
        assertNull(TaskHelper.getTriggerCondition(tm, conditions));
    }

    @Test
    public void testGetTriggerConditionWithNoConditionClass() {
        TaskManager tm = new MockTaskManager();
        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer(PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1"), new HashMap<String, Object>()));
        try {
            TaskHelper.getTriggerCondition(tm, conditions);
            fail("Should have thrown exception");
        } catch (HobsonRuntimeException ignored) {}
    }

    @Test
    public void testGetTriggerConditionWithNoTriggerCondition() {
        MockTaskManager tm = new MockTaskManager();
        PropertyContainerClassContext ccCtx = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1");
        tm.publishConditionClass(new TaskConditionClass(ccCtx, "foo", "") {
            @Override
            public ConditionClassType getConditionClassType() {
                return ConditionClassType.evaluator;
            }

            @Override
            public List<TypedProperty> createProperties() {
                return null;
            }

            @Override
            public boolean evaluate(ConditionEvaluationContext context, PropertyContainer values) {
                return false;
            }
        });
        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer(ccCtx, new HashMap<String, Object>()));
        assertNull(TaskHelper.getTriggerCondition(tm, conditions));
    }

    @Test
    public void testGetTriggerConditionWithTriggerCondition() {
        MockTaskManager tm = new MockTaskManager();
        PropertyContainerClassContext ccCtx1 = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc1");
        PropertyContainerClassContext ccCtx2 = PropertyContainerClassContext.create(PluginContext.createLocal("plugin1"), "cc2");
        tm.publishConditionClass(new TaskConditionClass(ccCtx1, "foo1", "") {
            @Override
            public ConditionClassType getConditionClassType() {
                return ConditionClassType.evaluator;
            }

            @Override
            public List<TypedProperty> createProperties() {
                return null;
            }

            @Override
            public boolean evaluate(ConditionEvaluationContext context, PropertyContainer values) {
                return false;
            }
        });
        tm.publishConditionClass(new TaskConditionClass(ccCtx2, "foo2", "") {
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
                return false;
            }
        });
        List<PropertyContainer> conditions = new ArrayList<>();
        conditions.add(new PropertyContainer(ccCtx2, new HashMap<String, Object>()));
        PropertyContainer pc = TaskHelper.getTriggerCondition(tm, conditions);
        assertNotNull(pc);
        assertEquals("cc2", pc.getContainerClassContext().getContainerClassId());
    }
}
