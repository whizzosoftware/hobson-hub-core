package com.whizzosoftware.hobson.bootstrap.api.task.actionset;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersistenceContext;
import com.whizzosoftware.hobson.api.persist.CollectionPersister;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerSet;
import com.whizzosoftware.hobson.api.task.TaskManager;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.*;

public class MapDBActionSetStore implements ActionSetStore, CollectionPersistenceContext {
    private DB db;
    private TaskManager taskManager;
    private CollectionPersister persister = new CollectionPersister();

    public MapDBActionSetStore(File file, TaskManager taskManager) {
        this.taskManager = taskManager;
        db = DBMaker.newFileDB(file)
            .closeOnJvmShutdown()
            .make();
    }

    @Override
    public Collection<PropertyContainerSet> getAllActionSets(HubContext ctx) {
        List<PropertyContainerSet> results = new ArrayList<>();
        Map<String,Object> fullDb = db.getAll();
        for (String key : fullDb.keySet()) {
            String actionSetId = persister.getActionSetIdFromKey(ctx, key);
            if (actionSetId != null) {
                results.add(persister.restoreActionSet(ctx, this, taskManager, actionSetId));
            }
        }
        return results;
    }

    @Override
    public PropertyContainerSet getActionSet(HubContext ctx, String actionSetId) {
        return persister.restoreActionSet(ctx, this, taskManager, actionSetId);
    }

    @Override
    public PropertyContainerSet addActionSet(HubContext ctx, String name, List<PropertyContainer> actions) {
        PropertyContainerSet tas = new PropertyContainerSet(UUID.randomUUID().toString(), null);
        List<PropertyContainer> al = new ArrayList<>();
        for (PropertyContainer ta : actions) {
            al.add(ta);
        }
        tas.setProperties(al);
        persister.saveActionSet(ctx, this, tas);
        return tas;
    }

    @Override
    public void deleteActionSet(String actionSetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getMap(String key) {
        return db.getTreeMap(key);
    }

    @Override
    public void commit() {
        db.commit();
    }
}
