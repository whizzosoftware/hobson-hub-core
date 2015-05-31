package com.whizzosoftware.hobson.bootstrap.api.task.actionset;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;

import java.util.Collection;
import java.util.List;

public interface ActionSetStore {
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx);
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId);
    public PropertyContainerSet addActionSet(HubContext ctx, String name, List<PropertyContainer> actions);
    public void deleteActionSet(String actionSetId);
}
