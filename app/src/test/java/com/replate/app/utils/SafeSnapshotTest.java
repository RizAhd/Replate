package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

/** Values written by the web panel or older app versions must never crash a listener. */
public class SafeSnapshotTest {

    @Test
    public void aPhoneNumberSavedAsANumberIsReadAsText() {
        assertEquals("771234567", SafeSnapshot.stringOf(771234567L));
        assertEquals("0771234567", SafeSnapshot.stringOf(" 0771234567 "));
    }

    @Test
    public void emptyOrStructuredValuesGiveNull() {
        assertNull(SafeSnapshot.stringOf(null));
        assertNull(SafeSnapshot.stringOf("   "));
        assertNull(SafeSnapshot.stringOf(Collections.singletonMap("a", "b")));
        assertNull(SafeSnapshot.stringOf(Collections.singletonList("a")));
    }

    @Test
    public void booleansAcceptRealAndTextValues() {
        assertTrue(SafeSnapshot.booleanOf(Boolean.TRUE, false));
        assertFalse(SafeSnapshot.booleanOf(Boolean.FALSE, true));
        assertTrue(SafeSnapshot.booleanOf("TRUE", false));
        assertFalse(SafeSnapshot.booleanOf(" false ", true));
    }

    @Test
    public void unknownBooleanValuesUseTheFallback() {
        assertTrue(SafeSnapshot.booleanOf(null, true));
        assertFalse(SafeSnapshot.booleanOf("maybe", false));
        assertFalse(SafeSnapshot.booleanOf(1L, false));
    }
}
