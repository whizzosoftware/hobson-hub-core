/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.rest.root;

import com.google.inject.Inject;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.hub.OIDCConfig;
import com.whizzosoftware.hobson.api.user.UserStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

/**
 * A resource to provide OpenID discovery information.
 *
 * @author Dan Noguerol
 */
public class OpenIdConfigurationResource extends ServerResource {
    public static final String PATH = "/.well-known/openid-configuration";

    @Inject
    HubManager hubManager;
    @Inject
    UserStore userStore;

    @Override
    protected Representation get() throws ResourceException {
        OIDCConfig config = hubManager.getOIDCConfiguration();

        JSONObject json = new JSONObject();
        json.put("issuer", config.getIssuer());
        json.put("authorization_endpoint", AuthorizationResource.PATH);
        json.put("userinfo_endpoint", "/v1/api/userInfo");
        json.put("token_endpoint", TokenResource.PATH);
        json.put("jwks_uri", JWKSResource.PATH);
        JSONArray ja = new JSONArray();
        ja.put("id_token");
        json.put("response_types_supported", ja);
        ja = new JSONArray();
        ja.put("public");
        json.put("subject_types_supported", ja);
        ja = new JSONArray();
        ja.put("openid");
        json.put("scopes_supported", ja);
        ja = new JSONArray();
        ja.put("RS256");
        json.put("id_token_signing_alg_values_supported", ja);
        ja = new JSONArray();
        ja.put("password");
        ja.put("implicit");
        json.put("grant_types_supported", ja);

        if (userStore.hasDefaultUser()) {
            getResponse().getHeaders().add("X-Default-User", userStore.getDefaultUser());
        }

        return new JsonRepresentation(json);
    }
}
