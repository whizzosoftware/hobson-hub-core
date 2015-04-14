/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.hub.HubManager;
import org.apache.commons.codec.digest.DigestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.security.User;
import org.restlet.security.Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Restlet verifier that authenticates against the local admin password.
 *
 * @author Dan Noguerol
 */
public class HobsonVerifier implements Verifier {
    private static final Logger logger = LoggerFactory.getLogger(HobsonVerifier.class);

    private HubManager hubManager;
    private HubContext localHubContext = HubContext.createLocal();

    @Override
    public int verify(Request request, Response response) {
        int result = RESULT_INVALID;
        try {
            if (request.getChallengeResponse() == null) {
                result = RESULT_MISSING;
            } else {
                String identifier = request.getChallengeResponse().getIdentifier();
                char[] secret = request.getChallengeResponse().getSecret();
                if (identifier.equals(localHubContext.getUserId()) && getHubManager().getLocalManager().authenticateAdmin(localHubContext, DigestUtils.sha256Hex(new String(secret)))) {
                    result = RESULT_VALID;
                    request.getClientInfo().setUser(new User("local", secret, "Local", "User", null));
                }
            }
        } catch (Throwable t) {
            logger.error("An exception occurred authenticating request", t);
        }

        return result;
    }

    protected HubManager getHubManager() {
        if (hubManager == null) {
            BundleContext ctx = FrameworkUtil.getBundle(getClass()).getBundleContext();
            ServiceReference ref = ctx.getServiceReference(HubManager.class.getName());
            if (ref != null) {
                try {
                    hubManager = (HubManager)ctx.getService(ref);
                } catch (Throwable e) {
                    throw new HobsonRuntimeException("Unable to obtain reference to HubManager", e);
                }
            } else {
                throw new HobsonRuntimeException("Unable to obtain reference to HubManager");
            }
        }
        return hubManager;
    }
}
