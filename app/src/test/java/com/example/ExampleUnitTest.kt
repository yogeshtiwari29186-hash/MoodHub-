package com.example

import com.example.data.crypto.CredentialSecurity
import com.example.model.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testWifiSecurityTypeParsing() {
        assertEquals(WifiSecurityType.WPA3_SAE, WifiSecurityType.fromCapabilities("[WPA3-SAE-CCMP][ESS]"))
        assertEquals(WifiSecurityType.WPA2_PSK, WifiSecurityType.fromCapabilities("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurityType.OPEN, WifiSecurityType.fromCapabilities("[ESS]"))
    }

    @Test
    fun testCredentialEncryptionAndDecryption() {
        val original = "MySuperSecretPass123!"
        val encrypted = CredentialSecurity.encrypt(original)
        assertNotEquals(original, encrypted)
        assertTrue(encrypted.isNotBlank())

        val decrypted = CredentialSecurity.decrypt(encrypted)
        assertEquals(original, decrypted)
    }
}
