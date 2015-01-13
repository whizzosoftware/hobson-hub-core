/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import org.apache.felix.bundlerepository.Property;
import org.osgi.framework.Version;

import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A property of an API capability.
 *
 * @author Dan Noguerol
 */
public class HobsonApiCapabilityProperty implements Property {
    private final String name;
    private final String type;
    private final String value;

    public HobsonApiCapabilityProperty(String name, String type, String value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Object getConvertedValue() {
        return convert(value, type);
    }

    private static Object convert(String value, String type) {
        // this method is taken right from PropertyImpl in the Apache Felix bundlerepository implementation
        try {
            if (value != null && type != null) {
                if (VERSION.equalsIgnoreCase(type)) {
                    return Version.parseVersion(value);
                } else if (URI.equalsIgnoreCase(type)) {
                    return new URI(value);
                } else if (URL.equalsIgnoreCase(type)) {
                    return new URL(value);
                } else if (LONG.equalsIgnoreCase(type)) {
                    return new Long(value);
                } else if (DOUBLE.equalsIgnoreCase(type)) {
                    return new Double(value);
                } else if (SET.equalsIgnoreCase(type)) {
                    StringTokenizer st = new StringTokenizer(value, ",");
                    Set s = new HashSet();
                    while (st.hasMoreTokens()) {
                        s.add(st.nextToken().trim());
                    }
                    return s;
                }
            }
            return value;
        } catch (Exception e) {
            IllegalArgumentException ex = new IllegalArgumentException();
            ex.initCause(e);
            throw ex;
        }
    }
}
