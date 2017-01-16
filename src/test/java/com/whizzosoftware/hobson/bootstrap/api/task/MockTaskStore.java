package com.whizzosoftware.hobson.bootstrap.api.task;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.task.HobsonTask;
import com.whizzosoftware.hobson.api.task.TaskContext;
import com.whizzosoftware.hobson.api.task.TaskHelper;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.task.store.TaskStore;

import java.util.*;

public class MockTaskStore implements TaskStore {
    private Map<TaskContext,HobsonTask> tasks = new HashMap<>();

    @Override
    public Collection<TaskContext> getAllTasks(HubContext hctx) {
        return tasks.keySet();
    }

    @Override
    public Collection<HobsonTask> getAllTasks(TaskManager taskManager, PluginContext pctx) {
        List<HobsonTask> results = new ArrayList<>();
        for (HobsonTask task : tasks.values()) {
            PropertyContainer triggerCondition = TaskHelper.getTriggerCondition(taskManager, task.getConditions());
            if (triggerCondition != null) {
                if (triggerCondition.getContainerClassContext().getPluginContext().equals(pctx)) {
                    results.add(task);
                }
            }
        }
        return results;
    }

    @Override
    public HobsonTask getTask(TaskContext context) {
        return tasks.get(context);
    }

    @Override
    public HobsonTask saveTask(HobsonTask task) {
        tasks.put(task.getContext(), task);
        return task;
    }

    @Override
    public void deleteTask(TaskContext context) {

    }

    @Override
    public void close() {

    }
}
