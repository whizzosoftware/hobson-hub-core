/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.api.variable;

import com.whizzosoftware.hobson.api.device.DeviceContext;
import com.whizzosoftware.hobson.api.variable.HobsonVariable;

/**
 * Concrete implementation of HobsonVariable.
 *
 * @author Dan Noguerol
 */
public class HobsonVariableImpl implements HobsonVariable {
    private DeviceContext ctx;
    private String name;
    private Object value;
    private String proxyType;
    private Mask mask;
    private Long lastUpdate;

    public HobsonVariableImpl(DeviceContext ctx, String name, Object value, Mask mask, String proxyType) {
        this.ctx = ctx;
        this.name = name;
        setValue(value);
        this.mask = mask;
        this.proxyType = proxyType;
    }

    @Override
    public String getPluginId() {
        return ctx.getPluginId();
    }

    @Override
    public boolean hasProxyType() {
        return (proxyType != null);
    }

    @Override
    public String getProxyType() {
        return proxyType;
    }

    @Override
    public String getDeviceId() {
        return ctx.getDeviceId();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Mask getMask() {
        return mask;
    }

    public void setValue(Object value) {
        this.value = value;
        this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public Long getLastUpdate() {
        return lastUpdate;
    }

    @Override
    public boolean isGlobal() {
        return (ctx.getDeviceId() == null);
    }

    public String toString() {
        return ctx.toString() + "." + name;
    }
}
