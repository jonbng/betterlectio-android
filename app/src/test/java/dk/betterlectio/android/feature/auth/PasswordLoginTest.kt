package dk.betterlectio.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordLoginTest {
    @Test
    fun credentials_require_non_blank() {
        assertFalse(PasswordLogin.Credentials("", "x", 1).isValid())
        assertFalse(PasswordLogin.Credentials("u", "", 1).isValid())
        assertFalse(PasswordLogin.Credentials("u", "p", 0).isValid())
        assertTrue(PasswordLogin.Credentials("u", "p", 94).isValid())
    }

    @Test
    fun loginFormFields_include_username_password() {
        val f = PasswordLogin.loginFormFields("elev", "secret")
        assertEquals("elev", f["username"])
        assertEquals("secret", f["password"])
        assertEquals("elev", f["m\$Content\$username"])
    }

    @Test
    fun loginPath_contains_login_aspx() {
        assertTrue(PasswordLogin.loginPath(517).contains("login.aspx"))
    }
}
