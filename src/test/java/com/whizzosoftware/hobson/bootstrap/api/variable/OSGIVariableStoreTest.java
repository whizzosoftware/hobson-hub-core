/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;
import com.whizzosoftware.hobson.api.variable.VariableContext;
import org.junit.Test;
import static org.junit.Assert.*;

public class OSGIVariableStoreTest {
    @Test
    public void testCreateFilter() {
        HubContext hctx = HubContext.createLocal();
        OSGIVariableStore s = new OSGIVariableStore();
        // all variables
        assertEquals("(&(objectClass=com.whizzosoftware.hobson.api.variable.HobsonVariable))", s.createFilter(HobsonVariable.class.getName(), VariableContext.create(hctx, null, null, null)));
        // all plugin variables
        assertEquals("(&(objectClass=com.whizzosoftware.hobson.api.variable.HobsonVariable)(pluginId=plugin1))", s.createFilter(HobsonVariable.class.getName(), VariableContext.create(PluginContext.create(hctx, "plugin1"), null, null)));
        // all plugin device variables
        assertEquals("(&(objectClass=com.whizzosoftware.hobson.api.variable.HobsonVariable)(pluginId=plugin1)(deviceId=device1))", s.createFilter(HobsonVariable.class.getName(), VariableContext.create(DeviceContext.create(hctx, "plugin1", "device1"), null)));
        // all global variables
        assertEquals("(&(objectClass=com.whizzosoftware.hobson.api.variable.HobsonVariable)(deviceId=$GLOBAL$))", s.createFilter(HobsonVariable.class.getName(), VariableContext.createGlobal(hctx, null, null)));
        // all global plugin variables
        assertEquals("(&(objectClass=com.whizzosoftware.hobson.api.variable.HobsonVariable)(pluginId=plugin1)(deviceId=$GLOBAL$))", s.createFilter(HobsonVariable.class.getName(), VariableContext.createGlobal(hctx, "plugin1", null)));
    }
}
