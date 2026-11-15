package com.example.qrcodeapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardapioLinksTest {

    // ---------- normalizar ----------

    @Test
    fun `adiciona https quando nao ha esquema`() {
        assertEquals("https://meurestaurante.com", CardapioLinks.normalizar("meurestaurante.com"))
    }

    @Test
    fun `mantem http e https existentes`() {
        assertEquals("http://site.com", CardapioLinks.normalizar("http://site.com"))
        assertEquals("https://site.com", CardapioLinks.normalizar("https://site.com"))
    }

    @Test
    fun `esquema em maiusculas nao e duplicado`() {
        assertEquals("http://site.com/x", CardapioLinks.normalizar("HTTP://site.com/x"))
        assertEquals("https://site.com", CardapioLinks.normalizar("Https://site.com"))
    }

    @Test
    fun `remove espacos nas pontas`() {
        assertEquals("https://site.com", CardapioLinks.normalizar("  site.com  "))
    }

    // ---------- linkValido ----------

    @Test
    fun `aceita links comuns`() {
        assertTrue(CardapioLinks.linkValido("meurestaurante.com/cardapio"))
        assertTrue(CardapioLinks.linkValido("https://meurestaurante.com.br/cardapio?lang=pt"))
        assertTrue(CardapioLinks.linkValido("HTTPS://site.com"))
    }

    @Test
    fun `rejeita links invalidos`() {
        assertFalse(CardapioLinks.linkValido(""))
        assertFalse(CardapioLinks.linkValido("   "))
        assertFalse(CardapioLinks.linkValido("restaurante"))
        assertFalse(CardapioLinks.linkValido("meu restaurante.com"))
        assertFalse(CardapioLinks.linkValido("ftp://site.com"))
        assertFalse(CardapioLinks.linkValido("https://.com"))
    }

    // ---------- montarUrlMesa ----------

    @Test
    fun `monta url simples`() {
        assertEquals(
            "https://meurestaurante.com/cardapio?mesa=12",
            CardapioLinks.montarUrlMesa("meurestaurante.com/cardapio", "12")
        )
    }

    @Test
    fun `remove barra final antes do parametro`() {
        assertEquals(
            "https://meurestaurante.com/cardapio?mesa=3",
            CardapioLinks.montarUrlMesa("https://meurestaurante.com/cardapio/", "3")
        )
    }

    @Test
    fun `usa e comercial quando ja existe query`() {
        assertEquals(
            "https://site.com/cardapio?lang=pt&mesa=7",
            CardapioLinks.montarUrlMesa("site.com/cardapio?lang=pt", "7")
        )
    }

    @Test
    fun `nao duplica separador quando link termina em interrogacao`() {
        assertEquals("https://site.com/c?mesa=1", CardapioLinks.montarUrlMesa("site.com/c?", "1"))
        assertEquals("https://site.com/c?a=1&mesa=2", CardapioLinks.montarUrlMesa("site.com/c?a=1&", "2"))
    }

    @Test
    fun `parametro fica antes do fragmento`() {
        assertEquals(
            "https://site.com/cardapio?mesa=5#bebidas",
            CardapioLinks.montarUrlMesa("site.com/cardapio#bebidas", "5")
        )
        assertEquals(
            "https://site.com/?x=1&mesa=5#/menu",
            CardapioLinks.montarUrlMesa("site.com/?x=1#/menu", "5")
        )
    }

    @Test
    fun `codifica o valor da mesa`() {
        assertEquals(
            "https://site.com/c?mesa=10+%26+11",
            CardapioLinks.montarUrlMesa("site.com/c", "10 & 11")
        )
    }

    @Test
    fun `esquema em maiusculas gera url correta`() {
        assertEquals(
            "http://site.com/c?mesa=4",
            CardapioLinks.montarUrlMesa("HTTP://site.com/c", "4")
        )
    }

    // ---------- ehLinkWeb ----------

    @Test
    fun `reconhece links lidos do QR`() {
        assertTrue(CardapioLinks.ehLinkWeb("https://site.com/cardapio?mesa=1"))
        assertTrue(CardapioLinks.ehLinkWeb("  HTTP://site.com  "))
    }

    @Test
    fun `nao trata texto comum como link`() {
        assertFalse(CardapioLinks.ehLinkWeb("Mesa 12"))
        assertFalse(CardapioLinks.ehLinkWeb("site.com"))
        assertFalse(CardapioLinks.ehLinkWeb("https://"))
        assertFalse(CardapioLinks.ehLinkWeb("WIFI:S:rede;T:WPA;P:senha;;"))
    }
}
