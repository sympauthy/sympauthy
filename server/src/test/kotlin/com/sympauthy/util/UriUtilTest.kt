package com.sympauthy.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class UriUtilTest {

    @Test
    fun `hasLocalHost - Is true for a name or an address reserved for a machine or a local network`() {
        listOf(
            "http://localhost:8080",
            "http://LOCALHOST",
            "http://auth.localhost",
            "http://sympauthy.local",
            "http://auth.corp.internal",
            "http://127.0.0.1:8080",
            "http://0.0.0.0",
            "http://10.0.0.5",
            "http://172.16.4.2",
            "http://192.168.1.10",
            "http://169.254.1.1",
            "http://[::1]:8080",
            "http://[fe80::1]",
            "http://[fd12:3456::1]",
            "/relative/path"
        ).forEach { uri ->
            assertTrue(URI.create(uri).hasLocalHost(), uri)
        }
    }

    @Test
    fun `hasLocalHost - Is false for a public name or address`() {
        listOf(
            "https://auth.example.com",
            "https://localhost.example.com",
            "http://203.0.113.7",
            "http://172.32.0.1",
            "http://[2001:db8::1]"
        ).forEach { uri ->
            assertFalse(URI.create(uri).hasLocalHost(), uri)
        }
    }
}
