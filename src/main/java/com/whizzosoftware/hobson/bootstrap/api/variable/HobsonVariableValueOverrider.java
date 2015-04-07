/*******************************************************************************
 * Copyright (c) 2015 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.variable.HobsonVariable;

/**
 * A wrapper class for performing variable value substitutions without altering the original variable's value.
 *
 * @author Dan Noguerol
 */
public class HobsonVariableValueOverrider implements HobsonVariable {
    private HobsonVariable variable;
    private Object value;

    public HobsonVariableValueOverrider(HobsonVariable variable, Object value) {
        this.variable = variable;
        this.value = value;
    }

    @Override
    public String getDeviceId() {
        return variable.getDeviceId();
    }

    @Override
    public Mask getMask() {
        return variable.getMask();
    }

    @Override
    public String getName() {
        return variable.getName();
    }

    @Override
    public Long getLastUpdate() {
        return variable.getLastUpdate();
    }

    @Override
    public String getPluginId() {
        return variable.getPluginId();
    }

    @Override
    public boolean hasProxyType() {
        return variable.hasProxyType();
    }

    @Override
    public String getProxyType() {
        return variable.getProxyType();
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public boolean isGlobal() {
        return variable.isGlobal();
    }
}
