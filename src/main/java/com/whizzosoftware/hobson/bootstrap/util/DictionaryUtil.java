/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import com.whizzosoftware.hobson.api.device.DeviceContext;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

/**
 * A convenience class for manipulating Dictionary objects.
 *
 * @author Dan Noguerol
 */
public class DictionaryUtil {

    /**
     * Updates a configuration dictionary with a Map of values.
     *
     * @param config the configuration dictionary to update
     * @param values the updated values
     * @param overwrite indicates whether an existing value should be overwritten
     *
     * @throws IOException on failure
     */
    static public void updateConfigurationDictionary(org.osgi.service.cm.Configuration config, Map<String,Object> values, boolean overwrite) throws IOException {
        Dictionary props = config.getProperties();
        if (props == null) {
            props = new Hashtable();
        }

        for (String key : values.keySet()) {
            if (props.get(key) == null || overwrite) {
                Object v = values.get(key);
                if (v instanceof DeviceContext) {
                    v = v.toString();
                }
                if (v != null) {
                    props.put(key, v);
                } else {
                    props.remove(key);
                }
            }
        }

        config.update(props);
    }
}
