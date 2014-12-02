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
import org.restlet.routing.Redirector;
import org.restlet.routing.Router;

/**
 * The root Restlet application that performs a redirect to the console. This is necessary so that if a user
 * hits localhost:8182 or localhost:8183 directly they get back something useful.
 *
 * @author Dan Noguerol
 */
public class RootApplication extends Application {
    @Override
    public Restlet createInboundRoot() {
        Router router = new Router();
        String target = "/console/index.html";
        Redirector redirector = new Redirector(getContext(), target, Redirector.MODE_CLIENT_TEMPORARY);
        router.attach("/", redirector);
        return router;
    }
}
