/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import ch.qos.logback.contrib.json.JsonFormatter;

import java.util.Map;

/**
 * A Logback JsonFormatter implementation that creates simple JSON via String concatenation.
 *
 * @author Dan Noguerol
 */
public class HobsonLogJsonFormatter implements JsonFormatter {
    @Override
    public String toJsonString(Map map) throws Exception {
        StringBuilder sb = new StringBuilder(150);
        sb.append("{\"time\":\"");
        sb.append(map.get("timestamp"));
        sb.append("\",\"thread\":\"");
        sb.append(map.get("thread"));
        sb.append("\",\"level\":\"");
        sb.append(map.get("level"));
        sb.append("\",\"message\":");
        sb.append(quote(map.get("message").toString()));
        if (map.containsKey("exception")) {
            sb.append(",\"exception\":");
            sb.append(quote(map.get("exception").toString()));
        }
        sb.append("}\n");
        return sb.toString();
    }

    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char c = 0;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    //                if (b == '<') {
                    sb.append('\\');
                    //                }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
