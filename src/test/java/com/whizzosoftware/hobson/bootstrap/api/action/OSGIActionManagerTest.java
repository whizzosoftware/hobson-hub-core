/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.action.Action;
import com.whizzosoftware.hobson.api.action.ActionLifecycleContext;
import com.whizzosoftware.hobson.api.action.job.Job;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OSGIActionManagerTest {
    @Test
    public void testCreateJob() {
        long now = System.currentTimeMillis();

        OSGIActionManager m = new OSGIActionManager();
        m.setMaxJobCount(2);
        assertEquals(0, m.getJobCount());
        Job job1 = m.createJob(new MockAction(), now);
        job1.start();
        assertEquals(1, m.getJobCount());
        Job job2 = m.createJob(new MockAction(), now);
        job2.start();
        assertEquals(2, m.getJobCount());
        try {
            m.createJob(new MockAction(), now).start();
        } catch (HobsonRuntimeException ignored) {}
        job1.complete();
        assertEquals(2, m.getJobCount());
        Job job3 = m.createJob(new MockAction(), now);
        job3.start();
        try {
            m.createJob(new MockAction(), now).start();
        } catch (HobsonRuntimeException ignored) {}
        Job job4 = m.createJob(new MockAction(), now + 3600000);
        job4.start();
        assertEquals(1, m.getJobCount());
    }

    private class MockAction implements Action {
        @Override
        public boolean isAssociatedWithPlugin(PluginContext ctx) {
            return false;
        }

        @Override
        public Future sendMessage(ActionLifecycleContext ctx, String msgName, Object prop) {
            ctx.complete();
            return null;
        }

        @Override
        public Future start(ActionLifecycleContext ctx) {
            return null;
        }

        @Override
        public Future stop(ActionLifecycleContext ctx) {
            return null;
        }
    }
}
