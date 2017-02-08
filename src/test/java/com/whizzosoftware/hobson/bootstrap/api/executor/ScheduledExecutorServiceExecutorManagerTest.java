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

import org.junit.Test;

import java.util.*;
import java.util.concurrent.Future;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduledExecutorServiceExecutorManagerTest {
    @Test
    public void testSubmit() throws Exception {
        final Object mutex1 = new Object();
        final Object mutex2 = new Object();
        final Object mutex3 = new Object();
        final List<Integer> started = new ArrayList<>();
        final List<Integer> completions = new ArrayList<>();

        ScheduledExecutorServiceExecutorManager m = new ScheduledExecutorServiceExecutorManager();

        Future f1 = m.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mutex1) {
                        started.add(1);
                        mutex1.wait();
                        completions.add(1);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Future f2 = m.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mutex2) {
                        started.add(2);
                        mutex2.wait();
                        completions.add(2);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Future f3 = m.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mutex3) {
                        started.add(3);
                        mutex3.wait();
                        completions.add(3);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        assertFalse(completions.contains(1));
        assertFalse(completions.contains(2));
        assertFalse(completions.contains(3));
        assertTrue(started.contains(1));
        assertTrue(started.contains(2));
        assertFalse(started.contains(3));

        synchronized (mutex1) {
            mutex1.notify();
        }

        while (!f1.isDone()) {
            Thread.sleep(10);
        }

        assertTrue(completions.contains(1));
        assertFalse(completions.contains(2));
        assertFalse(completions.contains(3));

        assertTrue(started.contains(1));
        assertTrue(started.contains(2));
        assertTrue(started.contains(3));

        synchronized (mutex2) {
            mutex2.notify();
        }

        while (!f2.isDone()) {
            Thread.sleep(10);
        }

        assertTrue(completions.contains(1));
        assertTrue(completions.contains(2));
        assertFalse(completions.contains(3));

        synchronized (mutex3) {
            mutex3.notify();
        }

        while (!f3.isDone()) {
            Thread.sleep(10);
        }

        assertTrue(completions.contains(1));
        assertTrue(completions.contains(2));
        assertTrue(completions.contains(3));
    }
}
