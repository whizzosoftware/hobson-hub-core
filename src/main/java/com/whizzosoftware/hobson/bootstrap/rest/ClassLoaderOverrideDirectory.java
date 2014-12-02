/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.rest;

import org.restlet.Context;
import org.restlet.resource.Directory;

/**
 * A Directory implementation that overrides the ClassLoader used for CLAP requests.
 *
 * @author Dan Noguerol
 */
public class ClassLoaderOverrideDirectory extends Directory {
    private ClassLoader bundleClassloader;

    public ClassLoaderOverrideDirectory(Context context, String rootUri, ClassLoader bundleClassloader) {
        super(context, rootUri);
        this.bundleClassloader = bundleClassloader;
    }

    public Context getContext() {
        Context context = super.getContext();
        context.setClientDispatcher(new CLAPCustomClassLoaderDispatcher(context.getClientDispatcher(), bundleClassloader));
        return context;
    }
}
