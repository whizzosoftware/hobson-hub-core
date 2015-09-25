/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import ch.qos.logback.contrib.json.JsonFormatter;

import java.util.Map;

public class HobsonActivityLogJsonFormatter implements JsonFormatter {
    @Override
    public String toJsonString(Map map) throws Exception {
        StringBuilder sb = new StringBuilder(150);
        sb.append("{\"timestamp\":\"");
        sb.append(map.get("timestamp"));
        sb.append("\",\"name\":");
        sb.append(JsonUtil.escape(map.get("message").toString()));
        sb.append("}\n");
        return sb.toString();
    }
}
