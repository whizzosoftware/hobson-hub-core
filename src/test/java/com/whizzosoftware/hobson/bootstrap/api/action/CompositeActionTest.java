package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.action.*;
import com.whizzosoftware.hobson.api.action.job.Job;
import com.whizzosoftware.hobson.api.plugin.EventLoopExecutor;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompositeActionTest {
    @Test
    public void testStartWithSyncCompletingActions() throws Exception {
        long now = System.currentTimeMillis();
        PluginContext pctx = PluginContext.createLocal("plugin1");
        final EventLoopGroup pluginEventLoop = new LocalEventLoopGroup(3);
        EventLoopExecutor pluginExecutor = new EventLoopExecutor() {
            @Override
            public Future executeInEventLoop(Runnable runnable) {
                return pluginEventLoop.submit(runnable);
            }
        };
        List<Action> actions = new ArrayList<>();
        MockImmediateCompleteAction a1 = new MockImmediateCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        MockImmediateCompleteAction a2 = new MockImmediateCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        MockImmediateCompleteAction a3 = new MockImmediateCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        actions.add(a1);
        actions.add(a2);
        actions.add(a3);

        CompositeAction ca = new CompositeAction(actions);
        Job job = new Job(ca, 2000, now);
        job.start();

        Thread.sleep(1000);

        assertTrue(a1.isOnStartCalled());
        assertTrue(a2.isOnStartCalled());
        assertTrue(a3.isOnStartCalled());
        assertFalse(job.isInProgress());
        assertTrue(job.isComplete());
    }

    @Test
    public void testStartWithAsyncCompletingActions() throws Exception {
        long now = System.currentTimeMillis();
        PluginContext pctx = PluginContext.createLocal("plugin1");
        final EventLoopGroup pluginEventLoop = new LocalEventLoopGroup(3);
        EventLoopExecutor pluginExecutor = new EventLoopExecutor() {
            @Override
            public Future executeInEventLoop(Runnable runnable) {
                return pluginEventLoop.submit(runnable);
            }
        };
        List<Action> actions = new ArrayList<>();
        MockEventCompleteAction a1 = new MockEventCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        MockEventCompleteAction a2 = new MockEventCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        MockEventCompleteAction a3 = new MockEventCompleteAction(pctx, new MockActionExecutionContext(), pluginExecutor);
        actions.add(a1);
        actions.add(a2);
        actions.add(a3);

        CompositeAction ca = new CompositeAction(actions);
        Job job = new Job(ca, 2000, now);
        job.start().sync();

        assertTrue(a1.isOnStartCalled());
        assertFalse(a2.isOnStartCalled());
        assertTrue(job.isInProgress());
        assertFalse(job.isComplete());

        job.message("complete", null).sync();

        Thread.sleep(500);

        assertTrue(a2.isOnStartCalled());
        assertTrue(job.isInProgress());
        assertFalse(job.isComplete());

        job.message("complete", null).sync();

        Thread.sleep(500);

        assertTrue(a3.isOnStartCalled());
        assertTrue(job.isInProgress());
        assertFalse(job.isComplete());

        job.message("complete", null).sync();

        Thread.sleep(500);

        assertFalse(job.isInProgress());
        assertTrue(job.isComplete());
    }
}
