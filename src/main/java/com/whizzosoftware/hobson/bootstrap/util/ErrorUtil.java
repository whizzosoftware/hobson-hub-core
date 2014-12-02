/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

/**
 * Class that provides convenience methods for error handling.
 *
 * @author Dan Noguerol
 */
public class ErrorUtil {
    /**
     * Return a JSON string for an exception.
     *
     * @param e an Exception object
     *
     * @return a JSON string
     */
    public static String getJSONFromException(Exception e) {
        return "{\"error\":\"" + e.getLocalizedMessage() + "\"}";
    }
}
