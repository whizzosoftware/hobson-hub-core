package com.whizzosoftware.hobson.bootstrap.api.user;

import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.telemetry.TelemetryManager;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.bootstrap.rest.oidc.LocalOIDCConfigProvider;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.*;

public class LocalUserStoreTest {
    @Test
    public void testAuthenticate() {
        HubManager hubManager = new HubManager() {
            @Override
            public String getVersion(HubContext hubContext) {
                return null;
            }

            @Override
            public Collection<HubContext> getAllHubs() {
                return null;
            }

            @Override
            public Collection<HubContext> getHubs(String userId) {
                return null;
            }

            @Override
            public HobsonHub getHub(HubContext ctx) {
                return null;
            }

            @Override
            public String getUserIdForHubId(String hubId) {
                return null;
            }

            @Override
            public void deleteConfiguration(HubContext ctx) {
            }

            @Override
            public boolean authenticateHub(HubCredentials credentials) {
                return false;
            }

            @Override
            public PropertyContainer getConfiguration(HubContext ctx) {
                return null;
            }

            @Override
            public PropertyContainerClass getConfigurationClass(HubContext ctx) {
                return null;
            }

            @Override
            public PropertyContainerClass getContainerClass(PropertyContainerClassContext ctx) {
                return null;
            }

            @Override
            public LineRange getLog(HubContext ctx, long startLine, long endLine, Appendable appendable) {
                return null;
            }

            @Override
            public LocalHubManager getLocalManager() {
                return new LocalHubManager() {
                    @Override
                    public NetworkInfo getNetworkInfo() {
                        return null;
                    }

                    @Override
                    public boolean authenticateLocal(HubContext ctx, String password) {
                        return (password.equals("local"));
                    }

                    @Override
                    public void setLocalPassword(HubContext ctx, PasswordChange change) {

                    }

                    @Override
                    public void addErrorLogAppender(Object aAppender) {

                    }

                    @Override
                    public void removeLogAppender(Object aAppender) {

                    }

                    @Override
                    public void addTelemetryManager(TelemetryManager manager) {

                    }

                    @Override
                    public void publishWebApplication(HubWebApplication app) {

                    }

                    @Override
                    public void unpublishWebApplication(String path) {

                    }
                };
            }

            @Override
            public Collection<String> getSerialPorts(HubContext hctx) {
                return null;
            }

            @Override
            public void sendTestEmail(HubContext ctx, PropertyContainer config) {

            }

            @Override
            public void sendEmail(HubContext ctx, String recipientAddress, String subject, String body) {

            }

            @Override
            public void setConfiguration(HubContext ctx, PropertyContainer configuration) {

            }
        };

        LocalUserStore mgr = new LocalUserStore();
        mgr.hubManager = hubManager;
        mgr.oidcConfigProvider = new LocalOIDCConfigProvider();
        HobsonUser user = mgr.authenticate("local", "local").getUser();
        assertNotNull(user);

        assertEquals("local", user.getId());
        assertEquals("Local", user.getGivenName());
        assertEquals("User", user.getFamilyName());
        assertNull(user.getEmail());
    }
}
