/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.event.HobsonEvent;
import com.whizzosoftware.hobson.api.event.MockEventManager;
import com.whizzosoftware.hobson.api.event.VariableUpdateRequestEvent;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.*;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OSGIVariableManagerTest {
    @Test
    public void testApplyVariableUpdates() {
        MockVariableStore vs = new MockVariableStore();
        MockEventManager em = new MockEventManager();
        OSGIVariableManager vm = new OSGIVariableManager(vs);
        vm.setEventManager(em);

        HubContext ctx = HubContext.createLocal();
        PluginContext pctx = PluginContext.create(ctx, "plugin");
        DeviceContext dctx = DeviceContext.create(pctx, "device");

        // try to apply an update to an unpublished device variable
        try {
            vm.fireVariableUpdateNotifications(ctx, Collections.singletonList(new VariableUpdate(VariableContext.create(dctx, "foo"), "bar")));
            fail("Should have thrown variable not found exception");
        } catch (VariableNotFoundException ignored) {
        }

        // try to apply an update to a published device variable with no value
        assertFalse(em.hasEvents());
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(dctx, "foo"), HobsonVariable.Mask.READ_WRITE, null));
        assertFalse(em.hasEvents());
        vm.fireVariableUpdateNotifications(ctx, Collections.singletonList(new VariableUpdate(VariableContext.create(dctx, "foo"), "bar")));
        assertTrue(em.hasEvents());
        assertEquals(1, em.getEventCount());
        List<VariableChange> up = (List<VariableChange>)em.getEvent(0).getProperties().get("updates");
        assertEquals(1, up.size());
        VariableChange u = up.get(0);
        assertEquals("foo", u.getName());
        assertEquals("bar", u.getNewValue());
        em.clearEvents();

        // try to apply an update to a published device variable with same value
        vm.fireVariableUpdateNotifications(ctx, Collections.singletonList(new VariableUpdate(VariableContext.create(dctx, "foo"), "bar")));
        assertTrue(em.hasEvents());
        assertEquals(1, em.getEventCount());
        up = (List<VariableChange>)em.getEvent(0).getProperties().get("updates");
        assertEquals(1, up.size());
        u = up.get(0);
        assertEquals("foo", u.getName());
        assertEquals("bar", u.getNewValue());
        em.clearEvents();

        // try to apply an update with a new null value
        vm.fireVariableUpdateNotifications(ctx, Collections.singletonList(new VariableUpdate(VariableContext.create(dctx, "foo"), null)));
        assertTrue(em.hasEvents());
        assertEquals(1, em.getEventCount());
        up = (List<VariableChange>)em.getEvent(0).getProperties().get("updates");
        assertEquals(1, up.size());
        u = up.get(0);
        assertEquals("foo", u.getName());
        assertNull(u.getNewValue());
    }

    @Test
    public void testPublishDeviceVariable() {
        MockVariableStore vs = new MockVariableStore();
        OSGIVariableManager vm = new OSGIVariableManager(vs);

        DeviceContext ctx = DeviceContext.createLocal("plugin", "device");

        vm.publishVariable(VariableContext.create(ctx, "foo"), "bar", HobsonVariable.Mask.READ_WRITE, null);

        Collection<HobsonVariable> r = vs.getDeviceVariables(ctx);
        assertEquals(1, r.size());

        HobsonVariable v = r.iterator().next();
        assertEquals(ctx.getPluginId(), v.getPluginId());
        assertEquals(ctx.getDeviceId(), v.getDeviceId());
        assertEquals(HobsonVariable.Mask.READ_WRITE, v.getMask());
        assertEquals("foo", v.getName());
        assertEquals("bar", v.getValue());
    }

    @Test
    public void testPublishDeviceVariableWithIllegalName() {
        MockVariableStore vs = new MockVariableStore();
        OSGIVariableManager vm = new OSGIVariableManager(vs);

        try {
            vm.publishVariable(VariableContext.createLocal("plugin", "device", "foo,f"), "bar", HobsonVariable.Mask.READ_WRITE, null);
            fail("Should have thrown exception");
        } catch (HobsonRuntimeException ignored) {}

        try {
            vm.publishVariable(VariableContext.createLocal("plugin", "device", "foo:f"), "bar", HobsonVariable.Mask.READ_WRITE, null);
            fail("Should have thrown exception");
        } catch (HobsonRuntimeException ignored) {}
    }

    @Test
    public void testPublishDuplicateDeviceVariable() {
        MockVariableStore vs = new MockVariableStore();
        OSGIVariableManager vm = new OSGIVariableManager(vs);

        vm.publishVariable(VariableContext.createLocal("plugin", "device", "foo"), "bar", HobsonVariable.Mask.READ_WRITE, null);

        try {
            vm.publishVariable(VariableContext.createLocal("plugin", "device", "foo"), "bar", HobsonVariable.Mask.READ_WRITE, null);
            fail("Should have thrown exception");
        } catch (HobsonRuntimeException ignored) {}
    }

    @Test
    public void testPublishGlobalVariable() {
        MockVariableStore vs = new MockVariableStore();
        OSGIVariableManager vm = new OSGIVariableManager(vs);

        PluginContext ctx = PluginContext.createLocal("plugin");

        vm.publishVariable(VariableContext.createGlobal(ctx, "foo"), "bar", HobsonVariable.Mask.READ_WRITE, null);

        Collection<HobsonVariable> r = vs.getAllGlobalVariables(ctx.getHubContext());
        assertEquals(1, r.size());

        HobsonVariable v = r.iterator().next();
        assertEquals(ctx.getPluginId(), v.getPluginId());
        assertEquals(DeviceContext.GLOBAL, v.getDeviceId());
        assertEquals(HobsonVariable.Mask.READ_WRITE, v.getMask());
        assertEquals("foo", v.getName());
        assertEquals("bar", v.getValue());
    }

    @Test
    public void testGetAllVariablesNoProxy() {
        VariableContext v1 = VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo");
        VariableContext v2 = VariableContext.create(DeviceContext.createLocal("plugin2", "device2"), "foo2");

        MockVariableStore vs = new MockVariableStore();
        vs.publishVariable(new MutableHobsonVariable(v1, HobsonVariable.Mask.READ_WRITE, "bar"));
        vs.publishVariable(new MutableHobsonVariable(v2, HobsonVariable.Mask.READ_WRITE, "bar2"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);
        Collection<HobsonVariable> vars = vm.getAllVariables(HubContext.createLocal());
        assertEquals(2, vars.size());

        for (HobsonVariable v : vars) {
            assertTrue((v.getPluginId().equals("plugin1") && v.getDeviceId().equals("device1") && v.getName().equals("foo") && v.getValue().equals("bar")) || (v.getPluginId().equals("plugin2") && v.getDeviceId().equals("device2") && v.getName().equals("foo2") && v.getValue().equals("bar2")));
        }
    }

    @Test
    public void testGetDeviceVariableNoProxy() {
        VariableContext v1 = VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo");
        VariableContext v2 = VariableContext.create(DeviceContext.createLocal("plugin2", "device2"), "foo");

        MockVariableStore vs = new MockVariableStore();
        vs.publishVariable(new MutableHobsonVariable(v1, HobsonVariable.Mask.READ_WRITE, "bar"));
        vs.publishVariable(new MutableHobsonVariable(v2, HobsonVariable.Mask.READ_WRITE, "bar2"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);
        HobsonVariable v = vm.getVariable(VariableContext.createLocal("plugin1", "device1", "foo"));
        assertNotNull(v);
        assertEquals("plugin1", v.getPluginId());
        assertEquals("device1", v.getDeviceId());
        assertEquals("foo", v.getName());
        assertEquals("bar", v.getValue());
    }

    @Test
    public void testGetDeviceVariablesNoProxy() {
        MockVariableStore vs = new MockVariableStore();
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo"), HobsonVariable.Mask.READ_WRITE, "bar"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo2"), HobsonVariable.Mask.READ_WRITE, "bar2"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin2", "device1"), "foo"), HobsonVariable.Mask.READ_WRITE, "bar3"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);
        Collection<HobsonVariable> hvc = vm.getDeviceVariables(DeviceContext.createLocal("plugin1", "device1"));
        assertNotNull(hvc);
        assertEquals(2, hvc.size());

        for (HobsonVariable v : hvc) {
            assertEquals("plugin1", v.getPluginId());
            assertEquals("device1", v.getDeviceId());
            assertTrue("foo".equals(v.getName()) || "foo2".equals(v.getName()));
            assertTrue("bar".equals(v.getValue()) || "bar2".equals(v.getValue()));
        }
    }

    @Test
    public void testGetGlobalVariable() {
        PluginContext pctx = PluginContext.createLocal("plugin2");

        MockVariableStore vs = new MockVariableStore();
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo"), HobsonVariable.Mask.READ_WRITE, "bar"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo2"), HobsonVariable.Mask.READ_WRITE, "bar2"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createGlobal(pctx), "sunrise"), HobsonVariable.Mask.READ_WRITE, "800"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);

        HobsonVariable v = vm.getVariable(VariableContext.createGlobal(pctx, "sunrise"));
        assertEquals("plugin2", v.getPluginId());
        assertEquals("sunrise", v.getName());
        assertEquals("800", v.getValue());
    }

    @Test
    public void testGetGlobalVariables() {
        MockVariableStore vs = new MockVariableStore();
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo"), HobsonVariable.Mask.READ_WRITE, "bar"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(DeviceContext.createLocal("plugin1", "device1"), "foo2"), HobsonVariable.Mask.READ_WRITE, "bar2"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.createGlobal(PluginContext.createLocal("plugin2"), "sunrise"), HobsonVariable.Mask.READ_WRITE, "800"));
        vs.publishVariable(new MutableHobsonVariable(VariableContext.createGlobal(PluginContext.createLocal("plugin3"), "sunset"), HobsonVariable.Mask.READ_WRITE, "1800"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);

        Collection<HobsonVariable> results = vm.getGlobalVariables(HubContext.createLocal());
        for (HobsonVariable v : results) {
            assertTrue("plugin2".equals(v.getPluginId()) || "plugin3".equals(v.getPluginId()));
            assertTrue("sunrise".equals(v.getName()) || "sunset".equals(v.getName()));
            assertTrue("800".equals(v.getValue()) || "1800".equals(v.getValue()));
        }
    }

    @Test
    public void testSetDeviceVariable() {
        MockEventManager em = new MockEventManager();
        MockVariableStore vs = new MockVariableStore();
        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");
        vs.publishVariable(new MutableHobsonVariable(VariableContext.create(dctx, "foo"), HobsonVariable.Mask.READ_WRITE, "bar"));

        OSGIVariableManager vm = new OSGIVariableManager(vs);
        vm.setEventManager(em);

        assertFalse(em.hasEvents());

        vm.setVariable(VariableContext.create(dctx, "foo"), "bar2");

        assertTrue(em.hasEvents());
        assertEquals(1, em.getEventCount());

        HobsonEvent e = em.getEvent(0);
        assertTrue(e instanceof VariableUpdateRequestEvent);

        VariableUpdateRequestEvent vure = (VariableUpdateRequestEvent)e;
        assertEquals(1, vure.getUpdates().size());
        VariableUpdate vu = vure.getUpdates().get(0);
        assertEquals("plugin1", vu.getPluginId());
        assertEquals("device1", vu.getDeviceId());
        assertEquals("foo", vu.getName());
        assertEquals("bar2", vu.getValue());
    }

    @Test
    public void testSetUnpublishedDeviceVariable() {
        MockEventManager em = new MockEventManager();
        MockVariableStore vs = new MockVariableStore();
        DeviceContext dctx = DeviceContext.createLocal("plugin1", "device1");

        OSGIVariableManager vm = new OSGIVariableManager(vs);
        vm.setEventManager(em);

        try {
            assertNotNull(vm.setVariable(VariableContext.create(dctx, "foo"), "bar2"));
            fail("Should have thrown exception");
        } catch (VariableNotFoundException ignored) {}
    }
}
