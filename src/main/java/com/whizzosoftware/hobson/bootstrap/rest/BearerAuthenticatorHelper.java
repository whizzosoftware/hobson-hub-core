/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import org.restlet.Response;
import org.restlet.data.*;
import org.restlet.engine.header.ChallengeWriter;
import org.restlet.engine.security.AuthenticatorHelper;
import org.restlet.util.Series;

import java.io.IOException;

public class BearerAuthenticatorHelper extends AuthenticatorHelper {
    public BearerAuthenticatorHelper() {
        super(ChallengeScheme.HTTP_OAUTH_BEARER, true, true);
    }

    @Override
    public void formatRequest(ChallengeWriter cw, ChallengeRequest challenge, Response response, Series<Header> httpHeaders) throws IOException {
        super.formatRequest(cw, challenge, response, httpHeaders);
        cw.appendQuotedChallengeParameter("realm", "Local Hobson Hub");
    }
}
