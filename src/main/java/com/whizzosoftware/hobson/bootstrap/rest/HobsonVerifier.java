/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.restlet.security.SecretVerifier;

/**
 * A Restlet verifier that authenticates against the local admin password.
 *
 * @author Dan Noguerol
 */
public class HobsonVerifier extends SecretVerifier {
    private HubManager hubManager;

    public HobsonVerifier(HubManager hubManager) {
        this.hubManager = hubManager;
    }

    @Override
    public int verify(String identifier, char[] secret) {
        if (identifier.equals("admin") && hubManager.authenticateAdmin(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, DigestUtils.sha256Hex(new String(secret)))) {
            return RESULT_VALID;
        } else {
            return RESULT_INVALID;
        }
    }
}
