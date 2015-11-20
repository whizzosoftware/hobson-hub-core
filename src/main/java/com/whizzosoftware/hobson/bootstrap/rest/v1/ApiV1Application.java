/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest.v1;

import com.whizzosoftware.hobson.bootstrap.rest.LocalAuthorizer;
import com.whizzosoftware.hobson.rest.v1.AbstractApiV1Application;
import org.restlet.routing.Router;
import org.restlet.security.Authorizer;

/**
 * The Hobson Hub REST API v1.
 *
 * @author Dan Noguerol
 */
public class ApiV1Application extends AbstractApiV1Application {
    @Override
    protected String getRealmName() {
        return "Hobson";
    }

    @Override
    protected Authorizer createAuthorizer() {
        return new LocalAuthorizer();
    }

    @Override
    protected void createAdditionalResources(Router secureRouter, Router insecureRouter) {
    }
}
