/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.util;

import com.whizzosoftware.hobson.bootstrap.util.HttpUtil;
import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

public class HttpUtilTest {
    @Test
    public void testCreateRange() throws ParseException {
        HttpUtil.ContentRange cr = HttpUtil.createRange("bytes=9500-9999", 10000);
        assertEquals(9500, cr.start);
        assertEquals(9999, cr.end);
        assertEquals(500, cr.length);

        cr = HttpUtil.createRange("bytes=-500", 10000);
        assertEquals(9500, cr.start);
        assertEquals(9999, cr.end);
        assertEquals(500, cr.length);

        cr = HttpUtil.createRange("bytes=9500-", 10000);
        assertEquals(9500, cr.start);
        assertEquals(9999, cr.end);
        assertEquals(500, cr.length);

        cr = HttpUtil.createRange("bytes=0-100", 101);
        assertEquals(0, cr.start);
        assertEquals(100, cr.end);
        assertEquals(101, cr.length);

        try {
            cr = HttpUtil.createRange(null, 10000);
            fail("Should have thrown exception");
        } catch (ParseException ignored) {}

        try {
            cr = HttpUtil.createRange("bytes=-", 10000);
            fail("Should have thrown exception");
        } catch (ParseException ignored) {}

        try {
            cr = HttpUtil.createRange("bytes=r-s", 10000);
            fail("Should have thrown exception");
        } catch (ParseException ignored) {}
    }
}
