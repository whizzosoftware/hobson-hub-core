/*
 *******************************************************************************
 * Copyright (c) 2017 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.executor;

import com.whizzosoftware.hobson.api.executor.ExecutorManager;

import java.util.concurrent.*;

/**
 * An ExecutorManager implementation that uses a ScheduledExecutorService.
 *
 * @author Dan Noguerol
 */
public class ScheduledExecutorServiceExecutorManager implements ExecutorManager {
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Executor Manager Thread");
        }
    });

    @Override
    public Future schedule(Runnable r, long initialDelay, long delay, TimeUnit unit) {
        return executorService.scheduleAtFixedRate(r, initialDelay, delay, unit);
    }

    @Override
    public Future submit(Runnable r) {
        return executorService.submit(r);
    }

    @Override
    public void cancel(Future f) {
        f.cancel(false);
    }
}
