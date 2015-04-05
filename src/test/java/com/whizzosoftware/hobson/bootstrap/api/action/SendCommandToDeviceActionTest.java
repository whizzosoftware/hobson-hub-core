package com.whizzosoftware.hobson.bootstrap.api.action;

import com.whizzosoftware.hobson.api.hub.HubContext;
import com.whizzosoftware.hobson.api.plugin.PluginContext;
import com.whizzosoftware.hobson.api.variable.VariableUpdate;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SendCommandToDeviceActionTest {
    @Test
    public void testCreateVariableUpdateForSetLevel() {
        SendCommandToDeviceAction a = new SendCommandToDeviceAction(PluginContext.create(HubContext.createLocal(), "pid"));
        Map<String,Object> p = new HashMap<String,Object>();
        p.put("pluginId", "com.whizzosoftware.hobson.hub.hobson-hub-zwave");
        p.put("deviceId", "zwave-40:1");
        p.put("commandId", "setLevel");
        p.put("param", 100);
        VariableUpdate v = a.createVariableUpdate(p);
        Assert.assertEquals("com.whizzosoftware.hobson.hub.hobson-hub-zwave", v.getPluginId());
        Assert.assertEquals("zwave-40:1", v.getDeviceId());
        Assert.assertEquals("level", v.getName());
        Assert.assertEquals(100, v.getValue());
    }

    @Test
    public void testCreateVariableUpdateForTurnOff() {
        SendCommandToDeviceAction a = new SendCommandToDeviceAction(PluginContext.create(HubContext.createLocal(), "pid"));
        Map<String,Object> p = new HashMap<String,Object>();
        p.put("pluginId", "com.whizzosoftware.hobson.hub.hobson-hub-zwave");
        p.put("deviceId", "zwave-40:1");
        p.put("commandId", "turnOff");
        VariableUpdate v = a.createVariableUpdate(p);
        Assert.assertEquals("com.whizzosoftware.hobson.hub.hobson-hub-zwave", v.getPluginId());
        Assert.assertEquals("zwave-40:1", v.getDeviceId());
        Assert.assertEquals("on", v.getName());
        Assert.assertEquals(false, v.getValue());
    }

    @Test
    public void testCreateVariableUpdateForTurnOn() {
        SendCommandToDeviceAction a = new SendCommandToDeviceAction(PluginContext.create(HubContext.createLocal(), "pid"));
        Map<String,Object> p = new HashMap<String,Object>();
        p.put("pluginId", "com.whizzosoftware.hobson.hub.hobson-hub-zwave");
        p.put("deviceId", "zwave-40:1");
        p.put("commandId", "turnOn");
        VariableUpdate v = a.createVariableUpdate(p);
        Assert.assertEquals("com.whizzosoftware.hobson.hub.hobson-hub-zwave", v.getPluginId());
        Assert.assertEquals("zwave-40:1", v.getDeviceId());
        Assert.assertEquals("on", v.getName());
        Assert.assertEquals(true, v.getValue());
    }

    @Test
    public void testCreateVariableUpdateForInvalidCommandId() {
        SendCommandToDeviceAction a = new SendCommandToDeviceAction(PluginContext.create(HubContext.createLocal(), "pid"));
        Map<String,Object> p = new HashMap<String,Object>();
        p.put("pluginId", "com.whizzosoftware.hobson.hub.hobson-hub-zwave");
        p.put("deviceId", "zwave-40:1");
        p.put("commandId", "ascnajdwqdqwdiq123**(&");
        try {
            a.createVariableUpdate(p);
            Assert.fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
        }
    }
}
