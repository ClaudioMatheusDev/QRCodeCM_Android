package com.example.qrcodeapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PratoTest {

    @Test
    fun `extrai id do prato`() {
        assertEquals("52772", Prato.extrairId("52772"))
        assertEquals("52772", Prato.extrairId("  52772  "))
        assertEquals("52772", Prato.extrairId("prato:52772"))
        assertEquals("52772", Prato.extrairId("Prato 52772"))
        assertEquals("52772", Prato.extrairId("PRATO#52772"))
    }

    @Test
    fun `ignora textos que nao sao codigo de prato`() {
        assertNull(Prato.extrairId("Mesa 12"))
        assertNull(Prato.extrairId("123"))                 // curto demais
        assertNull(Prato.extrairId("01001000"))            // longo demais (ex.: CEP)
        assertNull(Prato.extrairId("https://site.com/52772"))
        assertNull(Prato.extrairId(""))
    }
}
