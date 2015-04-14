/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import org.restlet.Context;
import org.restlet.Response;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Status;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.Verifier;
import org.restlet.util.Series;

import java.util.Map;
import java.util.logging.Level;

/**
 * A ChallengeAuthenticator implementation that allows the requester to change the status code returned when
 * an authentication failure occurs. This is done using the 'X-StatusOnLoginFail' header.
 *
 * @author Dan Noguerol
 */
public class AlterableStatusCodeChallengeAuthenticator extends ChallengeAuthenticator {
    public AlterableStatusCodeChallengeAuthenticator(Context context, boolean optional, ChallengeScheme challengeScheme, String realm) {
        super(context, optional, challengeScheme, realm);
    }

    public AlterableStatusCodeChallengeAuthenticator(Context context, boolean optional, ChallengeScheme challengeScheme, String realm, Verifier verifier) {
        super(context, optional, challengeScheme, realm, verifier);
    }

    public AlterableStatusCodeChallengeAuthenticator(Context context, ChallengeScheme challengeScheme, String realm) {
        super(context, challengeScheme, realm);
    }

    /**
     * Challenges the client by adding a challenge request to the response and
     * by setting the status to {@link org.restlet.data.Status#CLIENT_ERROR_UNAUTHORIZED}.
     *
     * @param response
     *            The response to update.
     * @param stale
     *            Indicates if the new challenge is due to a stale response.
     */
    @Override
    public void challenge(Response response, boolean stale) {
        boolean loggable = response.getRequest().isLoggable() && getLogger().isLoggable(Level.FINE);

        // retrieve the status code from the X-StatusOnLoginFail header if present
        Integer statusCode = null;
        Series headerMap = (Series)response.getRequest().getAttributes().get("org.restlet.http.headers");
        if (headerMap != null) {
            String sStatusCode = headerMap.getFirstValue("X-StatusOnLoginFail", true);
            if (sStatusCode != null) {
                try {
                    statusCode = Integer.parseInt(sStatusCode);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (loggable) {
            getLogger().log(Level.FINE, "An authentication challenge was requested.");
        }

        if (statusCode != null) {
            response.setStatus(new Status(statusCode));
        } else {
            response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED);
        }

        response.getChallengeRequests().add(createChallengeRequest(stale));
    }
}
