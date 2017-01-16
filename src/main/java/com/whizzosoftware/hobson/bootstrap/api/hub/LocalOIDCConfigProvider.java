/*
 *******************************************************************************
 * Copyright (c) 2016 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************
*/
package com.whizzosoftware.hobson.bootstrap.api.hub;

import com.whizzosoftware.hobson.api.hub.OIDCConfig;
import com.whizzosoftware.hobson.api.hub.OIDCConfigProvider;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A local implementation of OIDCConfigProvider.
 *
 * @author Dan Noguerol
 */
public class LocalOIDCConfigProvider implements OIDCConfigProvider {
    private static final Logger logger = LoggerFactory.getLogger(com.whizzosoftware.hobson.bootstrap.api.hub.LocalOIDCConfigProvider.class);

    private OIDCConfig config;

    public LocalOIDCConfigProvider() {
        try {
            RsaJsonWebKey rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
            rsaJsonWebKey.setKeyId("k1");
            config = new OIDCConfig("Hobson", "/login", "/token", "/userInfo", ".well-known/jwks.json", rsaJsonWebKey);
        } catch (JoseException e) {
            logger.error("Error creating OIDC configuration", e);
        }
    }

    @Override
    public OIDCConfig getConfig() {
        return config;
    }
}
