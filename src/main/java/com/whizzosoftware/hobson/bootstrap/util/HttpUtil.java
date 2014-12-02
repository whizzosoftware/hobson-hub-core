/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import java.text.ParseException;

/**
 * Helper class for methods involving HTTP-related functions.
 *
 * @author Dan Noguerol
 */
public class HttpUtil {

    /**
     * Create a ContentRange object for start and end values.
     *
     * @param start the start of the range
     * @param end the end of the range
     *
     * @return a ContentRange instance
     */
    static public ContentRange createRange(long start, long end) {
        return new ContentRange(start, end);
    }

    /**
     * Create a ContentRange object for an RFC-7233 Range string.
     *
     * @param range the Range string
     * @param contentLength the total length of the content
     *
     * @return a ContentRange instance (or null if the range can't be parsed)
     * @throws ParseException on failure
     */
    static public ContentRange createRange(String range, long contentLength) throws ParseException {
        if (range != null) {
            if (range.startsWith("bytes=")) {
                range = range.substring(6, range.length());
                int ix = range.indexOf("-");
                if (ix > -1 && range.length() > 1) {
                    try {
                        if (ix == 0) {
                            return new ContentRange(contentLength - Long.parseLong(range.substring(ix + 1, range.length())), contentLength - 1);
                        } else if (ix == range.length() - 1) {
                            return new ContentRange(Long.parseLong(range.substring(0, ix)), contentLength - 1);
                        } else {
                            return new ContentRange(Long.parseLong(range.substring(0, ix)), Long.parseLong(range.substring(ix + 1, range.length())));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        throw new ParseException("Unable to parse range", 0);
    }

    /**
     * A class representing a range within content.
     */
    static public class ContentRange {
        public long start;
        public long end;
        public int length;

        public ContentRange(long start, long end) {
            this.start = start;
            this.end = end;
            this.length = (int)(end - start + 1);
        }
    }
}
