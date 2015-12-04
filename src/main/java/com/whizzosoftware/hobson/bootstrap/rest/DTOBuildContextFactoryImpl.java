/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import com.google.inject.Inject;
import com.whizzosoftware.hobson.api.device.DeviceManager;
import com.whizzosoftware.hobson.api.hub.HubManager;
import com.whizzosoftware.hobson.api.persist.IdProvider;
import com.whizzosoftware.hobson.api.plugin.PluginManager;
import com.whizzosoftware.hobson.api.presence.PresenceManager;
import com.whizzosoftware.hobson.api.task.TaskManager;
import com.whizzosoftware.hobson.api.variable.VariableManager;
import com.whizzosoftware.hobson.dto.ExpansionFields;
import com.whizzosoftware.hobson.dto.context.DTOBuildContext;
import com.whizzosoftware.hobson.dto.context.DTOBuildContextFactory;
import com.whizzosoftware.hobson.dto.context.MediaProxyDTOBuildContext;

/**
 * An implementation of DTOBuildContextFactory that uses Hobson manager objects to obtain data.
 *
 * @author Dan Noguerol
 */
public class DTOBuildContextFactoryImpl implements DTOBuildContextFactory {
    @Inject
    DeviceManager deviceManager;
    @Inject
    HubManager hubManager;
    @Inject
    PluginManager pluginManager;
    @Inject
    PresenceManager presenceManager;
    @Inject
    TaskManager taskManager;
    @Inject
    VariableManager variableManager;
    @Inject
    IdProvider idProvider;

    @Override
    public DTOBuildContext createContext(String apiRoot, ExpansionFields expansions) {
        return new MediaProxyDTOBuildContext.Builder(apiRoot).
            deviceManager(deviceManager).
            hubManager(hubManager).
            pluginManager(pluginManager).
            presenceManager(presenceManager).
            taskManager(taskManager).
            variableManager(variableManager).
            expansionFields(expansions).
            idProvider(idProvider).
            build();
    }
}
