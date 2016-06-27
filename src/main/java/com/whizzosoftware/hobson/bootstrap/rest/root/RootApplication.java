/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest.root;

import com.whizzosoftware.hobson.rest.HobsonStatusService;
import org.restlet.Restlet;
import org.restlet.ext.guice.ResourceInjectingApplication;
import org.restlet.routing.Redirector;
import org.restlet.routing.Router;

/**
 * The root Restlet application that performs a redirect to the console and installs the OIDC resources.
 * This redirect is necessary so that if a user hits localhost:8182 or localhost:8183 directly they get back
 * something useful.
 *
 * @author Dan Noguerol
 */
public class RootApplication extends ResourceInjectingApplication {
    @Override
    public Restlet createInboundRoot() {
        setStatusService(new HobsonStatusService());

        Router router = newRouter();

        router.attach("/", new Redirector(getContext(), "/console/index.html", Redirector.MODE_CLIENT_TEMPORARY));

        // OIDC related resources
        router.attach(OpenIdConfigurationResource.PATH, OpenIdConfigurationResource.class);
        router.attach(JWKSResource.PATH, JWKSResource.class);
        router.attach(AuthorizationResource.PATH, AuthorizationResource.class);
        router.attach(TokenResource.PATH, TokenResource.class);

        return router;
    }
}
