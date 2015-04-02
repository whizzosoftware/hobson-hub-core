/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.action.AbstractHobsonAction;
import com.whizzosoftware.hobson.api.action.meta.ActionMetaData;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.util.UserUtil;

import java.util.Map;

/**
 * A HobsonAction implementation that sends e-mails.
 *
 * @author Dan Noguerol
 * @since hobson-hub-api 0.1.6
 */
public class EmailAction extends AbstractHobsonAction {
    public static final String RECIPIENT_ADDRESS = "recipientAddress";
    public static final String SUBJECT = "subject";
    public static final String MESSAGE = "message";

    /**
     * Constructor.
     *
     * @param pluginId the plugin ID creating this action
     *
     * @since hobson-hub-api 0.1.6
     */
    public EmailAction(String pluginId) {
        super(pluginId, "sendEmail", "Send E-mail");

        addMetaData(new ActionMetaData(RECIPIENT_ADDRESS, "Recipient Address", "The address the e-mail will be sent to", ActionMetaData.Type.STRING));
        addMetaData(new ActionMetaData(SUBJECT, "Subject", "The e-mail subject text", ActionMetaData.Type.STRING));
        addMetaData(new ActionMetaData(MESSAGE, "Message Body", "The e-mail message body", ActionMetaData.Type.STRING));
    }

    /**
     * Executes the action.
     *
     * @param hubManager a HubManager instance
     * @param properties a Map of action properties
     *
     * @since hobson-hub-api 0.1.6
     */
    @Override
    public void execute(HubManager hubManager, Map<String, Object> properties) {
        // send the e-mail
        hubManager.sendEmail(
            UserUtil.DEFAULT_USER,
            UserUtil.DEFAULT_HUB,
            (String) properties.get(RECIPIENT_ADDRESS),
            (String) properties.get(SUBJECT),
            (String) properties.get(MESSAGE)
        );
    }
}
