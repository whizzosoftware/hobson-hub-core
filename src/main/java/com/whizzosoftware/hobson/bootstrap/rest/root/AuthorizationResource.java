/*******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest.root;

import com.google.inject.Inject;
import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.user.UserStore;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.ext.guice.SelfInjectingServerResource;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;

/**
 * An OIDC authorization endpoint resource.
 *
 * @author Dan Noguerol
 */
public class AuthorizationResource extends SelfInjectingServerResource {
    public static final String PATH = "/authorize";

    @Inject
    UserStore userStore;

    @Override
    protected Representation get() throws ResourceException {
        String responseType = getQueryValue("response_type");
        String clientId = getQueryValue("client_id");
        String username = getQueryValue("username");
        String redirectUri = getQueryValue("redirect_uri");
        if ("token".equals(responseType)) {
            if ("hobson-webconsole".equals(clientId)) {
                StringBuilder sb = new StringBuilder("<html><body><form method=\"post\" action=\"/authorize\"><input type=\"hidden\" name=\"response_type\" value=\"token\" />");
                if (username != null) {
                    sb.append("<input type=\"hidden\" name=\"username\" value=\"").append(username).append("\" />");
                } else {
                    sb.append("<input type=\"text\" name=\"username\" />");
                }
                if (redirectUri != null) {
                    sb.append("<input type=\"hidden\" name=\"redirect_uri\" value=\"").append(redirectUri).append("\" />");
                }
                sb.append("<input type=\"text\" name=\"password\" /><input type=\"submit\" value=\"Login\" /></form></body></html>");
                StringRepresentation sr = new StringRepresentation(sb);
                sr.setMediaType(MediaType.TEXT_HTML);
                return sr;
            } else {
                throw new HobsonAuthenticationException("Unsupported client_id: " + clientId);
            }
        } else {
            throw new HobsonAuthenticationException("Unsupported response_type: " + responseType);
        }
    }

    @Override
    protected Representation post(Representation entity) throws ResourceException {
        Form form = new Form(entity);
        String responseType = form.getFirstValue("response_type");
        String username = form.getFirstValue("username");
        String password = form.getFirstValue("password");
        String redirectUri = form.getFirstValue("redirect_uri");
        if (redirectUri == null) {
            redirectUri = "/console/";
        }
        if ("token".equals(responseType)) {
            if (username != null && password != null) {
                UserAuthentication ua = userStore.authenticate(username, password);
                getResponse().redirectSeeOther(redirectUri + "#access_token=" + ua.getToken());
                return new EmptyRepresentation();
            } else {
                throw new HobsonAuthenticationException("Missing username and/or password");
            }
        } else {
            throw new HobsonAuthenticationException("Invalid response_type");
        }
    }
}
