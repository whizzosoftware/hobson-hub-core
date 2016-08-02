/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
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
    private ConditionClassType type;

    public MockTaskConditionClass(PluginContext pctx) {
        this(pctx, ConditionClassType.evaluator);
    }

    public MockTaskConditionClass(PluginContext pctx, ConditionClassType type) {
        super(PropertyContainerClassContext.create(pctx, "mock"), "Mock Condition Class", "");
        this.type = type;
    }

    @Override
    public ConditionClassType getConditionClassType() {
        return type;
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
