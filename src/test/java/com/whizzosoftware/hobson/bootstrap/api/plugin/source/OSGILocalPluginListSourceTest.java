/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Dictionary;
import java.util.Hashtable;

public class OSGILocalPluginListSourceTest {
    @Test
    public void testCreateDisplayNameFromSymbolicName() {
        // test with null headers
        OSGILocalPluginListSource p = new OSGILocalPluginListSource(null);
        assertEquals("foo", p.createDisplayNameFromSymbolicName(null, "foo"));

        // test with valid header
        Dictionary h = new Hashtable();
        h.put("Bundle-Name", "bar");
        assertEquals("bar", p.createDisplayNameFromSymbolicName(h, "foo"));

        // test with no header
        h = new Hashtable();
        assertEquals("foo", p.createDisplayNameFromSymbolicName(h, "foo"));
    }

}
