/*******************************************************************************
 * Copyright (c) 2014 Whizzo Software, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.whizzosoftware.hobson.bootstrap.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;

/**
 * Advertises the presence of the Hub via mDNS.
 *
 * @author Dan Noguerol
 */
public class Advertiser {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String name;
    private JmDNS jmdns;

    public Advertiser(String name) {
        this.name = name;
    }

    public void start() throws IOException {
        jmdns = JmDNS.create();
        jmdns.registerService(ServiceInfo.create("_hobson._tcp.local.", name, 8080, 0, 0, " "));
        logger.info("JmDNS advertiser started");
    }

    public void stop() throws IOException {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            jmdns.close();
            logger.debug("JmDNS advertiser stopped");
        }
    }
}
