package com.veonadtech.pixeltracker

import org.junit.Assert.*
import org.junit.Test

class InitStatusTest {

    @Test
    fun `Success has default message`() {
        val s = InitStatus.Success()
        assertEquals("SDK initialized", s.message)
    }

    @Test
    fun `Success accepts custom message`() {
        val s = InitStatus.Success("SDK already initialized")
        assertEquals("SDK already initialized", s.message)
    }

    @Test
    fun `Failure stores exception and reason`() {
        val ex = IllegalArgumentException("bad url")
        val f = InitStatus.Failure(ex, "Invalid configuration")
        assertSame(ex, f.exception)
        assertEquals("Invalid configuration", f.reason)
    }

    @Test
    fun `Success is a subtype of InitStatus`() {
        val s: InitStatus = InitStatus.Success()
        assertNotNull(s)
    }

    @Test
    fun `Failure is a subtype of InitStatus`() {
        val f: InitStatus = InitStatus.Failure(Exception("x"), "reason")
        assertNotNull(f)
    }

    @Test
    fun `when expression exhaustively handles both subtypes`() {
        fun handle(status: InitStatus): String = when (status) {
            is InitStatus.Success -> "ok"
            is InitStatus.Failure -> "fail"
        }
        assertEquals("ok", handle(InitStatus.Success()))
        assertEquals("fail", handle(InitStatus.Failure(Exception(), "r")))
    }

    @Test
    fun `two Success with same message are equal`() {
        assertEquals(InitStatus.Success("msg"), InitStatus.Success("msg"))
    }

    @Test
    fun `two Failure with same args are equal`() {
        val ex = RuntimeException("boom")
        assertEquals(
            InitStatus.Failure(ex, "r"),
            InitStatus.Failure(ex, "r")
        )
    }
}