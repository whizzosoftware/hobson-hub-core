/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * A Restlet application for the Hobson Hub setup wizard.
 *
 * @author Dan Noguerol
 */
public class SetupApplication extends Application {
    @Override
    public Restlet createInboundRoot() {
        Router router = new Router();
        router.attach("/", new ClassLoaderOverrideDirectory(getContext(), "clap://class/setup/", getClass().getClassLoader()));
        return router;
    }
}
