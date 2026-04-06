package org.light.challenge.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class AppTest {

    @Test
    fun `valid args parse correctly`() {
        val invoice = parseInvoice(arrayOf("12000", "Marketing", "false"))
        assertEquals(BigDecimal("12000"), invoice.amount)
        assertEquals("Marketing", invoice.department)
        assertFalse(invoice.requiresManagerApproval)
    }

    @Test
    fun `requiresManagerApproval true parses correctly`() {
        val invoice = parseInvoice(arrayOf("7500", "Finance", "true"))
        assertTrue(invoice.requiresManagerApproval)
    }

    @Test
    fun `requiresManagerApproval is case-insensitive`() {
        assertTrue(parseInvoice(arrayOf("7500", "Finance", "TRUE")).requiresManagerApproval)
        assertFalse(parseInvoice(arrayOf("7500", "Finance", "FALSE")).requiresManagerApproval)
        assertTrue(parseInvoice(arrayOf("7500", "Finance", "True")).requiresManagerApproval)
    }

    @Test
    fun `too few args throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { parseInvoice(arrayOf("12000", "Marketing")) }
        assertTrue(ex.message!!.contains("3"))
    }

    @Test
    fun `too many args throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { parseInvoice(arrayOf("12000", "Marketing", "false", "extra")) }
        assertTrue(ex.message!!.contains("3"))
    }

    @Test
    fun `non-numeric amount throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { parseInvoice(arrayOf("abc", "Marketing", "false")) }
        assertTrue(ex.message!!.contains("abc"))
    }

    @Test
    fun `negative amount throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { parseInvoice(arrayOf("-100", "Marketing", "false")) }
        assertTrue(ex.message!!.contains("non-negative"))
    }

    @Test
    fun `invalid requiresManagerApproval value throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> { parseInvoice(arrayOf("5000", "Finance", "yes")) }
        assertTrue(ex.message!!.contains("yes"))
    }

    @Test
    fun `zero amount is valid`() {
        val invoice = parseInvoice(arrayOf("0", "Finance", "false"))
        assertEquals(BigDecimal.ZERO, invoice.amount)
    }

    @Test
    fun `decimal amount parses correctly`() {
        val invoice = parseInvoice(arrayOf("9999.99", "Engineering", "true"))
        assertEquals(BigDecimal("9999.99"), invoice.amount)
    }
}
