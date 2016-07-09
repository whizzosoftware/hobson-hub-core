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
import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.user.UserStore;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.ext.guice.SelfInjectingServerResource;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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
        String error = getQueryValue("error_description");
        if ("token".equals(responseType)) {
            if ("hobson-webconsole".equals(clientId)) {
                StringBuilder sb = new StringBuilder("<html> <head><style>p { font-family: \"Open Sans\", Helvetica, sans-serif } .field {border-style: solid;border-width: 1px;border-color: #ccc;box-shadow: inset 0 1px 2px rgba(0,0,0,0.1);color: rgba(0,0,0,0.75);font-size: 0.875rem;padding: 0.5rem;border-radius: 3px;} .error { color: red; }</style></head> <body> <div style=\"text-align: center; margin: 40px;\"> <img src=data:image/png;base64,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> <form method=post action=/authorize> <input type=hidden name=response_type value=token> <div style=\"margin-top: 25px;\"> ");
                if (error != null) {
                    sb.append("<p class=\"error\">").append(error).append("</p>");
                }
                if (username != null) {
                    sb.append("<input class=\"field\" type=\"hidden\" name=\"username\" value=\"").append(username).append("\" /><br/>");
                } else {
                    sb.append("<input class=\"field\" type=\"text\" name=\"username\" />");
                }
                if (redirectUri != null) {
                    sb.append("<input type=\"hidden\" name=\"redirect_uri\" value=\"").append(redirectUri).append("\" />");
                }
                sb.append("<input class=field type=password name=password placeholder=Password> </div> <div style=\"margin-top: 15px;\">");
                sb.append("<input style=\"background-color: #10649e; margin: 0; padding: 1em 3em; font-weight: 200; border: none; letter-spacing: 2px; border-radius: 3px; color: #fff;\" type=submit value=LOGIN> </div> </form> </div> </body> </html>");
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
                try {
                    UserAuthentication ua = userStore.authenticate(username, password);
                    getResponse().redirectSeeOther(redirectUri + "#access_token=" + ua.getToken() + "&token_type=bearer&id_token=" + ua.getToken());
                } catch (HobsonAuthenticationException e) {
                    sendErrorRedirect(redirectUri, "invalid_request", e.getLocalizedMessage());
                }
            } else {
                sendErrorRedirect(redirectUri, "invalid_request", "Missing username and/or password");
            }
        } else {
            sendErrorRedirect(redirectUri, "invalid_request", "Invalid response type");
        }
        return new EmptyRepresentation();
    }

    private void sendErrorRedirect(String redirectUri, String code, String msg) {
        try {
            getResponse().redirectSeeOther(
                redirectUri + "?error=" + URLEncoder.encode(code, "UTF-8") +
                        "&error_description=" + URLEncoder.encode(msg, "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new HobsonRuntimeException("Error creating redirect URL", e);
        }
    }
}
