/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.plugin.source;

import org.apache.felix.bundlerepository.Capability;
import org.apache.felix.bundlerepository.Property;

import java.util.*;

/**
 * Represents a capability provided by the currently installed API. This is used to determine whether remote plugins
 * are compatible with the current runtime.
 *
 * @author Dan Noguerol
 */
public class HobsonApiCapability implements Capability {
    private List<Property> properties = new ArrayList<>();
    private final Map<String, String> directives = new HashMap();
    private Map<String,Object> propMap = new HashMap<>();

    public HobsonApiCapability(String pkg, String version) {
        addProperty(new HobsonApiCapabilityProperty("package", "STRING", pkg));
        addProperty(new HobsonApiCapabilityProperty("version", "VERSION", version));
    }

    public void addProperty(Property p) {
        properties.add(p);
        propMap.put(p.getName().toLowerCase(), p.getConvertedValue());
    }

    @Override
    public String getName() {
        return "package";
    }

    @Override
    public Property[] getProperties() {
        return properties.toArray(new Property[properties.size()]);
    }

    @Override
    public Map getPropertiesAsMap() {
        return propMap;
    }

    @Override
    public Map<String, String> getDirectives() {
        return Collections.unmodifiableMap(directives);
    }
}
