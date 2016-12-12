package com.whizzosoftware.hobson.bootstrap.api.user;

import com.whizzosoftware.hobson.api.HobsonAuthenticationException;
import com.whizzosoftware.hobson.api.hub.*;
import com.whizzosoftware.hobson.api.property.PropertyContainer;
import com.whizzosoftware.hobson.api.property.PropertyContainerClass;
import com.whizzosoftware.hobson.api.property.PropertyContainerClassContext;
import com.whizzosoftware.hobson.api.data.DataStreamManager;
import com.whizzosoftware.hobson.api.user.HobsonRole;
import com.whizzosoftware.hobson.api.user.HobsonUser;
import com.whizzosoftware.hobson.api.user.UserAuthentication;
import com.whizzosoftware.hobson.api.variable.GlobalVariable;
import com.whizzosoftware.hobson.api.variable.GlobalVariableContext;
import com.whizzosoftware.hobson.bootstrap.api.hub.LocalOIDCConfigProvider;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class LocalUserStoreTest {
    @Test
    public void testGetUsers() throws Exception {
        File f = File.createTempFile("users", "db");
        f.deleteOnExit();
        LocalUserStore s = new LocalUserStore(f);
        Collection<HobsonUser> users = s.getUsers();
        assertEquals(1, users.size());
        HobsonUser u = users.iterator().next();
        assertEquals("admin", u.getId());
        assertEquals("Administrator", u.getGivenName());
        assertEquals("User", u.getFamilyName());
        assertEquals(1, u.getRoles().size());
        assertEquals("administrator", u.getRoles().iterator().next().name());
    }

    @Test
    public void testAddUser() throws Exception {
        File f = File.createTempFile("users", "db");
        f.deleteOnExit();

        LocalUserStore s = new LocalUserStore(f);
        s.setHubManager(new MockHubManager(new LocalOIDCConfigProvider()));

        // add a new user
        s.addUser("test", "test", "Test", "User", Collections.singletonList(HobsonRole.userRead));

        // make sure we can authenticate
        UserAuthentication a = s.authenticate("test", "test");
        assertEquals("test", a.getUser().getId());

        // make sure we can pull info
        HobsonUser u = s.getUser("test");
        assertNotNull(u);
        assertEquals("test", u.getId());
        assertEquals("Test", u.getGivenName());
        assertEquals("User", u.getFamilyName());
        assertEquals(1, u.getRoles().size());
        assertEquals("userRead", u.getRoles().iterator().next().name());
    }

    @Test
    public void testChangePassword() throws Exception {
        File f = File.createTempFile("users", "db");
        f.deleteOnExit();

        LocalUserStore s = new LocalUserStore(f);
        s.setHubManager(new MockHubManager(new LocalOIDCConfigProvider()));

        UserAuthentication a = s.authenticate("admin", "password");
        assertEquals("admin", a.getUser().getId());

        s.changeUserPassword("admin", new PasswordChange("password", "password2"));

        a = s.authenticate("admin", "password2");
        assertEquals("admin", a.getUser().getId());

        try {
            s.authenticate("admin", "password");
            fail("Should have thrown exception");
        } catch (HobsonAuthenticationException ignored) {}
    }

    @Test
    public void testAuthenticate() throws Exception {
        HubManager hubManager = new HubManager() {
            @Override
            public String getVersion(HubContext hubContext) {
                return null;
            }

            @Override
            public Collection<HubContext> getHubs() {
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
            public OIDCConfig getOIDCConfiguration() {
                return new LocalOIDCConfigProvider().getConfig();
            }

            @Override
            public HobsonUser convertTokenToUser(String token) {
                return null;
            }

            @Override
            public boolean hasPropertyContainerClass(PropertyContainerClassContext ctx) {
                return false;
            }

            @Override
            public LineRange getLog(HubContext ctx, long startLine, long endLine, Appendable appendable) {
                return null;
            }

            @Override
            public LocalHubManager getLocalManager() {
                return new LocalHubManager() {
                    private LocalOIDCConfigProvider p = new LocalOIDCConfigProvider();

                    @Override
                    public NetworkInfo getNetworkInfo() {
                        return null;
                    }

                    @Override
                    public void addErrorLogAppender(Object aAppender) {

                    }

                    @Override
                    public void removeLogAppender(Object aAppender) {

                    }

                    @Override
                    public void setWebSocketUri(String uri) {

                    }

                    @Override
                    public void addDataStreamManager(DataStreamManager manager) {

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

            @Override
            public void setGlobalVariable(GlobalVariableContext gctx, Object value, long timestamp) {

            }

            @Override
            public void setGlobalVariables(Map<GlobalVariableContext, Object> values, long timestamp) {

            }

            @Override
            public GlobalVariable getGlobalVariable(GlobalVariableContext gctx) {
                return null;
            }

            @Override
            public Collection<GlobalVariable> getGlobalVariables(HubContext hctx) {
                return null;
            }
        };

        File f = File.createTempFile("users", "db");
        f.deleteOnExit();
        LocalUserStore mgr = new LocalUserStore();
        mgr.setHubManager(hubManager);
        HobsonUser user = mgr.authenticate("admin", "password").getUser();
        assertNotNull(user);

        assertEquals("admin", user.getId());
        assertEquals("Administrator", user.getGivenName());
        assertEquals("User", user.getFamilyName());
        assertNull(user.getEmail());
    }
}
