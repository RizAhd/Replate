package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// Checks that the hotel key is read correctly from a chat id.
public class ChatKeysTest {

    @Test
    public void findsTheHotelKeyOfACustomerChat() {
        assertEquals("hotelA", ChatKeys.hotelKeyForCustomerChat("hotelA_user1", "user1"));
        assertEquals("hotel_with_underscores", ChatKeys.hotelKeyForCustomerChat("hotel_with_underscores_u9", "u9"));
    }

    @Test
    public void ignoresChatsThatBelongToSomeoneElse() {
        assertNull(ChatKeys.hotelKeyForCustomerChat("hotelA_user2", "user1"));
        assertNull(ChatKeys.hotelKeyForCustomerChat("user1_hotelA", "user1"));
    }

    @Test
    public void ignoresMissingOrEmptyParts() {
        assertNull(ChatKeys.hotelKeyForCustomerChat(null, "user1"));
        assertNull(ChatKeys.hotelKeyForCustomerChat("hotelA_user1", null));
        assertNull(ChatKeys.hotelKeyForCustomerChat("hotelA_user1", ""));
        assertNull(ChatKeys.hotelKeyForCustomerChat("_user1", "user1"));
    }
}
