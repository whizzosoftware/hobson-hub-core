/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.whizzosoftware.hobson.rest.HobsonAuthorizer;
import com.whizzosoftware.hobson.rest.HobsonRestContext;
import com.whizzosoftware.hobson.rest.HobsonRole;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ClientInfo;
import org.restlet.security.Authorizer;

/**
 * An authorizer that only allows access to the logged-in user's data and only to the hub with ID "local".
 *
 * @author Dan Noguerol
 */
public class LocalAuthorizer extends Authorizer implements HobsonAuthorizer {
    @Override
    protected boolean authorize(Request request, Response response) {
        String path = request.getResourceRef().getPath();
        HobsonRestContext ctx = HobsonRestContext.createContext(getApplication(), path);
        ClientInfo clientInfo = request.getClientInfo();
        request.getAttributes().put(HUB_CONTEXT, ctx);
        return (
            clientInfo.getRoles().contains(HobsonRole.ADMIN.value()) ||
            (clientInfo.getUser().getIdentifier().equals(ctx.getUserId()) && (ctx.getHubId() == null || "local".equals(ctx.getHubId())) && clientInfo.getRoles().contains(HobsonRole.USER.value()))
        );
    }
}
