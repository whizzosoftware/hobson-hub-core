/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.whizzosoftware.hobson.api.HobsonRuntimeException;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.util.UserUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.security.User;
import org.restlet.security.Verifier;

/**
 * A Restlet verifier that authenticates against the local admin password.
 *
 * @author Dan Noguerol
 */
public class HobsonVerifier implements Verifier {
    private HubManager hubManager;

    @Override
    public int verify(Request request, Response response) {
        int result = RESULT_INVALID;
        if (request.getChallengeResponse() == null) {
            result = RESULT_MISSING;
        } else {
            String identifier = request.getChallengeResponse().getIdentifier();
            char[] secret = request.getChallengeResponse().getSecret();
            if (identifier.equals("local") && getHubManager().getLocalManager().authenticateAdmin(UserUtil.DEFAULT_USER, UserUtil.DEFAULT_HUB, DigestUtils.sha256Hex(new String(secret)))) {
                result = RESULT_VALID;
                request.getClientInfo().setUser(new User("local", secret, "Local", "User", null));
            }
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
