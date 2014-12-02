/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Helper class for functions involving the setup wizard.
 *
 * @author Dan Noguerol
 */
public class SetupWizardUtil {
    private static final Logger logger = LoggerFactory.getLogger(SetupWizardUtil.class);

    static public Boolean isSetupWizardComplete() {
        try {
            Configuration config = getConfiguration();
            Dictionary props = config.getProperties();
            return (props != null && (Boolean)props.get("setup.complete"));
        } catch (IOException e) {
            logger.error("Unable to determine if setup wizard is complete", e);
        }
        return false;
    }

    static public void setSetupWizardComplete(boolean complete) throws IOException {
        Configuration config = getConfiguration();
        Dictionary props = config.getProperties();
        if (props == null) {
            props = new Hashtable();
        }
        props.put("setup.complete", complete);
        config.update(props);
    }

    static private Configuration getConfiguration() throws IOException {
        BundleContext context = FrameworkUtil.getBundle(SetupWizardUtil.class).getBundleContext();
        ServiceReference ref = context.getServiceReference(ConfigurationAdmin.class.getName());
        ConfigurationAdmin configAdmin = (ConfigurationAdmin)context.getService(ref);
        return configAdmin.getConfiguration(context.getBundle().getSymbolicName());
    }
}
