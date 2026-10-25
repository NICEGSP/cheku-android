package com.cheku.app.net

import com.cheku.app.config.parseCertificatePins
import com.cheku.app.config.siteDomainOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class HttpPinningTest {
    private val domains = setOf("example.com")

    @Test
    fun pinsOwnDomainAndItsSubdomainsOnly() {
        assertTrue(HttpPinning.isPinnedHost("example.com", domains))
        assertTrue(HttpPinning.isPinnedHost("www.example.com", domains))
        assertTrue("大小写不敏感", HttpPinning.isPinnedHost("WWW.Example.Com", domains))

        assertFalse("第三方油价来源不锁定", HttpPinning.isPinnedHost("www.autohome.com.cn", domains))
        assertFalse(HttpPinning.isPinnedHost("www.icauto.com.cn", domains))
        assertFalse("后缀相同但不是子域", HttpPinning.isPinnedHost("evil-example.com", domains))
        assertFalse(HttpPinning.isPinnedHost(null, domains))
        assertFalse(HttpPinning.isPinnedHost("", domains))
        assertFalse("未配置域名时不锁定任何主机", HttpPinning.isPinnedHost("www.example.com", emptySet()))
    }

    @Test
    fun anyCertificateInTheChainMayMatch() {
        val pins = setOf("LEAF", "ROOT")

        assertTrue("叶子命中即通过", HttpPinning.matchesPins(listOf("LEAF", "OTHER"), pins))
        assertTrue("续签后叶子变了，但根仍命中", HttpPinning.matchesPins(listOf("NEW-LEAF", "ROOT"), pins))
        assertFalse("整条链都不在集合内", HttpPinning.matchesPins(listOf("NEW-LEAF", "NEW-ROOT"), pins))
        assertFalse(HttpPinning.matchesPins(emptyList(), pins))
    }

    @Test
    fun siteDomainAndPinListAreParsedFromBuildConfigValues() {
        assertEquals("example.com", siteDomainOf("https://www.example.com"))
        assertEquals("api.example.com", siteDomainOf("https://api.example.com/x"))
        assertEquals("", siteDomainOf(""))
        assertEquals(setOf("A=", "B="), parseCertificatePins(" A=, B=;A= "))
        assertTrue(parseCertificatePins("").isEmpty())
    }

    @Test
    fun spkiHashMatchesOpensslEncoding() {
        val der = ByteArray(0x40) { it.toByte() }
        val expected = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(der)
        )
        assertEquals(expected, HttpPinning.sha256Base64(der))
        assertFalse(HttpPinning.sha256Base64(der).contains("\n"))
    }
}
