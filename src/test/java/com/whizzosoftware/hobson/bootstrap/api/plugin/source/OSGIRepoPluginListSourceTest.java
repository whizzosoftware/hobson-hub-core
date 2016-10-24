/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import com.whizzosoftware.hobson.api.plugin.HobsonPluginDescriptor;
import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.Requirement;
import org.apache.felix.bundlerepository.Resource;
import org.junit.Test;
import org.osgi.framework.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class OSGIRepoPluginListSourceTest {
    @Test
    public void testGetPluginsWithTwoDifferentVersions() {
        List<Resource> resources = new ArrayList<>();
        resources.add(new MockResource("foo", new Version(0, 1, 1)));
        resources.add(new MockResource("foo", new Version(0, 1, 0)));

        OSGIRepoPluginListSource pls = new OSGIRepoPluginListSource(null, null);
        Map<String,HobsonPluginDescriptor> pds = pls.getPlugins(resources.toArray(new Resource[resources.size()]));
        assertEquals(1, pds.size());
        assertNotNull(pds.get("foo"));
        HobsonPluginDescriptor pd = pds.get("foo");
        assertEquals("0.1.1", pd.getVersion());
    }

    private class MockResource implements Resource {
        private String id;
        private Version version;

        public MockResource(String id, Version version) {
            this.id = id;
            this.version = version;
        }

        @Override
        public Map getProperties() {
            return null;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getSymbolicName() {
            return id;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public String getPresentationName() {
            return null;
        }

        @Override
        public String getURI() {
            return null;
        }

        @Override
        public Long getSize() {
            return null;
        }

        @Override
        public String[] getCategories() {
            return new String[0];
        }

        @Override
        public Capability[] getCapabilities() {
            return new Capability[0];
        }

        @Override
        public Requirement[] getRequirements() {
            return new Requirement[0];
        }

        @Override
        public boolean isLocal() {
            return false;
        }
    }
}
