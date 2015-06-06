/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

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

    static public void updateConfigurationDictionary(org.osgi.service.cm.Configuration config, Map<String,Object> values, boolean overwrite) throws IOException {
        Dictionary props = config.getProperties();
        if (props == null) {
            props = new Hashtable();
        }

        for (String key : values.keySet()) {
            if (props.get(key) == null || overwrite) {
                props.put(key, values.get(key));
            }
        }

        config.update(props);
    }
}
