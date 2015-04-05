/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest.v1;

import com.whizzosoftware.hobson.api.HobsonAuthException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.rest.v1.Authorizer;

/**
 * An Authorizer implementation that only allows access to user "local" and hub "local". This is only useful when
 * a user is accessing resources directly on a local hub.
 *
 * @author Dan Noguerol
 */
public class LocalAuthorizer implements Authorizer {
    @Override
    public void authorizeHub(HubContext ctx) {
        if (!"local".equals(ctx.getHubId()) || !"local".equals(ctx.getUserId())) {
            throw new HobsonAuthException("User ID and hub ID should be local/local");
        }
    }
}
