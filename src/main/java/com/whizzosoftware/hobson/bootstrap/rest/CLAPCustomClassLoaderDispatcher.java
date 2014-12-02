/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;

/**
 * A Restlet implementation that wrappers another Restlet instance and sets the ClassLoader to use for CLAP
 * requests.
 *
 * @author Dan Noguerol
 */
public class CLAPCustomClassLoaderDispatcher extends Restlet {
    private Restlet dispatcher;
    private ClassLoader bundleClassLoader;

    public CLAPCustomClassLoaderDispatcher(Restlet dispatcher, ClassLoader bundleClassLoader) {
        this.dispatcher = dispatcher;
        this.bundleClassLoader = bundleClassLoader;
    }

    public void handle(Request request, Response response) {
        if (request.getResourceRef().getScheme().equalsIgnoreCase("clap")) {
            request.getAttributes().put("org.restlet.clap.classLoader", bundleClassLoader);
        }
        dispatcher.handle(request, response);
    }
}
