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
import org.jose4j.jwk.RsaJsonWebKey;
import org.restlet.ext.guice.SelfInjectingServerResource;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.ResourceException;

/**
 * A resource to provide JSON web keys.
 *
 * @author Dan Noguerol
 */
public class JWKSResource extends SelfInjectingServerResource {
    public static final String PATH = "/.well-known/jwks.json";

    @Inject
    HubManager hubManager;

    @Override
    protected Representation get() throws ResourceException {
        OIDCConfig config = hubManager.getOIDCConfiguration();
        RsaJsonWebKey key = (RsaJsonWebKey)config.getSigningKey();
        return new JsonRepresentation("{\"keys\":[" + key.toJson() + "]}");
    }
}
