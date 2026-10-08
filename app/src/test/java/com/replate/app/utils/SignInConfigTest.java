package com.replate.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Checks that missing or placeholder sign-in keys count as not set up.
public class SignInConfigTest {

    @Test
    public void placeholdersAreNotConfigured() {
        assertFalse(SignInConfig.isConfigured("YOUR_WEB_CLIENT_ID_HERE"));
        assertFalse(SignInConfig.isConfigured("YOUR_FACEBOOK_APP_ID"));
        assertFalse(SignInConfig.isConfigured("  YOUR_FACEBOOK_APP_ID "));
    }

    @Test
    public void emptyValuesAreNotConfigured() {
        assertFalse(SignInConfig.isConfigured(null));
        assertFalse(SignInConfig.isConfigured(""));
        assertFalse(SignInConfig.isConfigured("   "));
    }

    @Test
    public void realValuesAreConfigured() {
        assertTrue(SignInConfig.isConfigured("1234567890-abc.apps.googleusercontent.com"));
        assertTrue(SignInConfig.isConfigured("1234567890123456"));
    }
}
