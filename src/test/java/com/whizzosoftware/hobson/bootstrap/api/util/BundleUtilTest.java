package com.whizzosoftware.hobson.bootstrap.api.util;

import com.whizzosoftware.hobson.api.plugin.PluginStatus;
import static org.junit.Assert.*;
import org.junit.Test;
import org.osgi.framework.Bundle;

public class BundleUtilTest {
    @Test
    public void testPluginStatusFromBundleState() {
        assertEquals(PluginStatus.Code.RUNNING, BundleUtil.createPluginStatusFromBundleState(Bundle.ACTIVE).getCode());
        assertEquals(PluginStatus.Code.STOPPED, BundleUtil.createPluginStatusFromBundleState(Bundle.INSTALLED).getCode());
        assertEquals(PluginStatus.Code.STOPPED, BundleUtil.createPluginStatusFromBundleState(Bundle.RESOLVED).getCode());
        assertEquals(PluginStatus.Code.NOT_INSTALLED, BundleUtil.createPluginStatusFromBundleState(Bundle.UNINSTALLED).getCode());
    }
}
