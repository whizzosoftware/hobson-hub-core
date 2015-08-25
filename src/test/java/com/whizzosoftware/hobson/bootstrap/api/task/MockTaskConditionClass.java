package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.property.TypedProperty;
import com.whizzosoftware.hobson.api.task.condition.ConditionClassType;
import com.whizzosoftware.hobson.api.task.condition.ConditionEvaluationContext;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClass;

import java.util.List;

public class MockTaskConditionClass extends TaskConditionClass {

    public MockTaskConditionClass(PluginContext pctx) {
        super(PropertyContainerClassContext.create(pctx, "mock"), "Mock Condition Class", "");
    }

    @Override
    public ConditionClassType getType() {
        return ConditionClassType.evaluator;
    }

    @Override
    public List<TypedProperty> createProperties() {
        return null;
    }

    @Override
    public boolean evaluate(ConditionEvaluationContext context, PropertyContainer values) {
        return values.getBooleanPropertyValue("result");
    }
}
