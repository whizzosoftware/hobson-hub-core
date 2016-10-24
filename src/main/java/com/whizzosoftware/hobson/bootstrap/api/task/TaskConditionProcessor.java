/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.condition.ConditionClassType;
import com.whizzosoftware.hobson.api.task.condition.ConditionEvaluationContext;
import com.whizzosoftware.hobson.api.task.condition.TaskConditionClass;
import com.whizzosoftware.hobson.api.variable.DeviceVariableContext;
import com.whizzosoftware.hobson.api.variable.DeviceVariableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class for processing task conditions.
 *
 * @author Dan Noguerol
 */
public class TaskConditionProcessor {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Evaluates a task's evaluator conditions.
     *
     * @param taskManager the task manager instance to use for evaluation
     * @param context the context of the task to be evaluated
     *
     * @return a boolean indicating whether any of the task's conditions evaluated to false
     */
    public boolean evaluate(final TaskManager taskManager, final HobsonTask task, final HubManager hubManager, final DeviceManager deviceManager, final TaskContext context) {
        boolean conditionFailure = false;

        logger.trace("Evaluating conditions for task: {}", context);

        // evaluate all task conditions
        if (task.hasConditions()) {
            for (PropertyContainer pc : task.getConditions()) {
                TaskConditionClass pcc = taskManager.getConditionClass(pc.getContainerClassContext());
                if (pcc != null) {
                    if (pcc.getConditionClassType() == ConditionClassType.evaluator && !pcc.evaluate(new ConditionEvaluationContext() {
                        @Override
                        public DeviceVariableState getDeviceVariableState(DeviceVariableContext dvctx) {
                            return deviceManager.getDeviceVariable(dvctx);
                        }
                    }, pc)) {
                        conditionFailure = true;
                        break;
                    }
                } else {
                    logger.error("Unable to evaluate conditions for task {}: no condition class found matching {}", context, pc.getContainerClassContext());
                    throw new HobsonRuntimeException("Unable to find condition class: " + pc.getContainerClassContext());
                }
            }
        }

        return !conditionFailure;
    }
}
