package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginList;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.PluginType;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class PluginListTest {
    @Test
    public void testConstructorWithNull() {
        PluginList list = new PluginList(null);
        assertEquals(0, list.size());
        assertEquals(0, list.getUpdatesAvailable());
    }

    @Test
    public void testConstructorWithEmptyList() {
        List<PluginDescriptor> plugins = new ArrayList<PluginDescriptor>();
        PluginList list = new PluginList(plugins);
        assertEquals(0, list.size());
        assertEquals(0, list.getPlugins().size());
        assertEquals(0, list.getUpdatesAvailable());
    }

    @Test
    public void testUpdates() {
        List<PluginDescriptor> plugins = new ArrayList<PluginDescriptor>();
        PluginDescriptor pd = new PluginDescriptor("id1", "name1", "desc1", PluginType.CORE, new PluginStatus(PluginStatus.Status.RUNNING), "1.0.0");
        pd.setLatestVersionString("1.1.0");
        plugins.add(pd);
        plugins.add(new PluginDescriptor("id2", "name2", "desc2", PluginType.CORE, new PluginStatus(PluginStatus.Status.RUNNING), "1.0.0"));

        PluginList list = new PluginList(plugins);
        assertEquals(2, list.size());
        assertEquals(2, list.getPlugins().size());
        assertEquals(1, list.getUpdatesAvailable());
    }
}
