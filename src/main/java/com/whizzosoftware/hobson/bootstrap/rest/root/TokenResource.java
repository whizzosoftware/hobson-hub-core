/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest.root;

import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.user.UserStore;
import org.json.JSONObject;
import org.restlet.data.Form;
import org.restlet.ext.guice.SelfInjectingServerResource;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;

import javax.inject.Inject;

/**
 * An OIDC token endpoint resource.
 *
 * @author Dan Noguerol
 */
public class TokenResource extends SelfInjectingServerResource {
    public static final String PATH = "/token";

    @Inject
    UserStore userStore;

    @Override
    protected Representation post(Representation entity) throws ResourceException {
        final Form form = new Form(entity);

        String grantType = form.getFirstValue("grant_type");

        if ("password".equals(grantType) || "token".equals(grantType)) {
            String username = form.getFirstValue("username");
            String password = form.getFirstValue("password");
            if (username != null && password != null) {
                UserAuthentication ua = userStore.authenticate(username, password);
                if ("password".equals(grantType)) {
                    JSONObject json = new JSONObject();
                    json.put("id_token", ua.getToken());
                    return new JsonRepresentation(json);
                } else {
                    return new EmptyRepresentation();
                }
            } else {
                throw new HobsonAuthenticationException("Missing username and/or password");
            }
        } else {
            throw new HobsonAuthenticationException("Invalid grant_type");
        }
    }
}
