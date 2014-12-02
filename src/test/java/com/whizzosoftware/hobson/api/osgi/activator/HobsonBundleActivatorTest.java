package com.whizzosoftware.hobson.api.osgi.activator;

import org.junit.Test;
import static org.junit.Assert.*;

public class HobsonBundleActivatorTest {
    @Test
    public void testGetHobsonPluginClass() {
        HobsonBundleActivator a = new HobsonBundleActivator();
        assertEquals("com.whizzosoftware.hobson.openweathermap.OpenWeatherMapPlugin", a.getHobsonPluginClass("Provide-Capability: hobson.plugin=com.whizzosoftware.hobson.openweathermap.OpenWeatherMapPlugin"));
    }
}
