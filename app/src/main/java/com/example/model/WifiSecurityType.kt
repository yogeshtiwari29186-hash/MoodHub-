package com.example.model

enum class WifiSecurityType(val displayName: String, val isSecure: Boolean) {
    OPEN("Open", false),
    WEP("WEP", true),
    WPA_PSK("WPA", true),
    WPA2_PSK("WPA2", true),
    WPA3_SAE("WPA3", true),
    EAP("Enterprise (802.1x)", true),
    OWE("OWE (Enhanced Open)", true),
    UNKNOWN("Secured", true);

    companion object {
        fun fromCapabilities(capabilities: String): WifiSecurityType {
            val upper = capabilities.uppercase()
            return when {
                upper.contains("SAE") || upper.contains("WPA3") -> WPA3_SAE
                upper.contains("WPA2") || upper.contains("RSN") -> WPA2_PSK
                upper.contains("WPA") -> WPA_PSK
                upper.contains("WEP") -> WEP
                upper.contains("EAP") -> EAP
                upper.contains("OWE") -> OWE
                upper.contains("PSK") -> WPA2_PSK
                upper.isEmpty() || (!upper.contains("WEP") && !upper.contains("WPA") && !upper.contains("PSK")) -> OPEN
                else -> UNKNOWN
            }
        }
    }
}
