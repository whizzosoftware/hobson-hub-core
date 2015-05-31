package com.whizzosoftware.hobson.bootstrap.api.user;

import com.whizzosoftware.hobson.api.user.HobsonUser;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalUserStoreTest {
    @Test
    public void testAuthenticate() {
        LocalUserStore mgr = new LocalUserStore();
        HobsonUser user = mgr.authenticate("local", "local");
        assertNotNull(user);

        assertEquals("local", user.getId());
        assertEquals("Local", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertNull(user.getEmail());
    }
}
