package com.portfolioiq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdsTest {
    @Test void keepsAHarmlessInboundId() {
        assertEquals("abc-123_DEF.456", RequestIds.resolve("abc-123_DEF.456"));
    }

    @Test void replacesMissingOrUnsafeIds() {
        for (String bad : new String[]{null, "", "short", "has space in it", "line\nbreak-injection", "x".repeat(65), "semi;colon-id", "<script>alert(1)</script>"}) {
            String id = RequestIds.resolve(bad);
            assertNotEquals(bad, id);
            assertTrue(id.matches("[0-9a-f-]{36}"), id);
        }
    }
}
