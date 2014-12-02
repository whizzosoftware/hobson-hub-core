package com.whizzosoftware.hobson.bootstrap.api.plugin;

import com.whizzosoftware.hobson.api.plugin.PluginDescriptor;
import com.whizzosoftware.hobson.api.plugin.PluginList;
import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import com.whizzosoftware.hobson.api.plugin.PluginType;
import com.whizzosoftware.hobson.bootstrap.api.plugin.source.MockPluginListSource;
import org.junit.Test;

import static org.junit.Assert.*;

public class PluginListBuilderTest {
    @Test
    public void testCreatePluginListWithOneUpdate() throws Exception {
        MockPluginListSource localSource = new MockPluginListSource();
        localSource.addPluginDescriptor(new PluginDescriptor("id", "name", null, PluginType.PLUGIN, new PluginStatus(PluginStatus.Status.RUNNING), "1.0.0"));

        MockPluginListSource repoSource = new MockPluginListSource();
        PluginDescriptor pd = new PluginDescriptor("id", "name", "desc", PluginType.PLUGIN, new PluginStatus(PluginStatus.Status.NOT_INSTALLED), null);
        pd.setLatestVersionString("1.1.0");
        repoSource.addPluginDescriptor(pd);

        PluginListBuilder p = new PluginListBuilder(localSource, repoSource);

        PluginList pl = p.createPluginList();
        assertEquals(1, pl.size());
        pd = pl.getPlugins().get(0);
        assertEquals("id", pd.getId());
        assertEquals("name", pd.getName());
        assertEquals("desc", pd.getDescription());
        assertEquals(PluginStatus.Status.RUNNING, pd.getStatus().getStatus());
        assertTrue(pd.hasCurrentVersion());
        assertEquals("1.0.0", pd.getCurrentVersionString());
        assertTrue(pd.hasLaterVersion());
        assertEquals("1.1.0", pd.getLatestVersionString());
    }

    @Test
    public void testCreatePluginListWithNoLocalPlugins() throws Exception {
        MockPluginListSource localSource = new MockPluginListSource();

        MockPluginListSource repoSource = new MockPluginListSource();
        PluginDescriptor pd = new PluginDescriptor("id", "name", "desc", PluginType.PLUGIN, new PluginStatus(PluginStatus.Status.NOT_INSTALLED), null);
        pd.setLatestVersionString("1.1.0");
        repoSource.addPluginDescriptor(pd);

        PluginListBuilder p = new PluginListBuilder(localSource, repoSource);

        PluginList pl = p.createPluginList();
        assertEquals(1, pl.size());
        pd = pl.getPlugins().get(0);
        assertEquals("id", pd.getId());
        assertEquals("name", pd.getName());
        assertEquals("desc", pd.getDescription());
        assertEquals(PluginStatus.Status.NOT_INSTALLED, pd.getStatus().getStatus());
        assertFalse(pd.hasCurrentVersion());
        assertNull(pd.getCurrentVersionString());
        assertTrue(pd.hasLaterVersion());
        assertEquals("1.1.0", pd.getLatestVersionString());
    }

    @Test
    public void testCreatePluginListWithOnlyLocalPlugins() throws Exception {
        MockPluginListSource localSource = new MockPluginListSource();
        PluginDescriptor pd = new PluginDescriptor("id", "name", "desc", PluginType.PLUGIN, new PluginStatus(PluginStatus.Status.RUNNING), "1.1.0");
        localSource.addPluginDescriptor(pd);

        PluginListBuilder p = new PluginListBuilder(localSource, new MockPluginListSource());

        PluginList pl = p.createPluginList();
        assertEquals(1, pl.size());
        pd = pl.getPlugins().get(0);
        assertEquals("id", pd.getId());
        assertEquals("name", pd.getName());
        assertEquals("desc", pd.getDescription());
        assertEquals(PluginStatus.Status.RUNNING, pd.getStatus().getStatus());
        assertTrue(pd.hasCurrentVersion());
        assertEquals("1.1.0", pd.getCurrentVersionString());
        assertFalse(pd.hasLaterVersion());
        assertNull(pd.getLatestVersionString());
    }

    @Test
    public void testIsRepoVersionNewer() {
        PluginListBuilder p = new PluginListBuilder(null, null);
        assertTrue(p.isRepoVersionNewer("0.0.0", "0.0.1"));
        assertTrue(p.isRepoVersionNewer("0.0.1", "0.0.2"));
        assertFalse(p.isRepoVersionNewer("0.0.1", "0.0.0"));
        assertFalse(p.isRepoVersionNewer("0.0.2", "0.0.1"));

        assertTrue(p.isRepoVersionNewer("1.0.0", "2.0.0"));
        assertTrue(p.isRepoVersionNewer("1.0.1", "2.0.1"));
        assertFalse(p.isRepoVersionNewer("2.0.0", "1.0.0"));
        assertFalse(p.isRepoVersionNewer("2.0.1", "1.0.1"));

        assertTrue(p.isRepoVersionNewer("2.0.0", "2.1.0"));
        assertTrue(p.isRepoVersionNewer("2.1.0", "2.2.0"));
        assertFalse(p.isRepoVersionNewer("2.1.0", "2.0.0"));
        assertFalse(p.isRepoVersionNewer("2.2.0", "2.1.0"));
    }
}
