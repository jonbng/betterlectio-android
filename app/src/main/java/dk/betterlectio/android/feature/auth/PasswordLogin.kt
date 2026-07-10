package dk.betterlectio.android.feature.auth

/**
 * Builds UniLogin / Lectio username+password form fields (Flutter-style password login).
 * Pure helpers for tests; [PasswordLoginRepository] performs the HTTP dance.
 */
object PasswordLogin {
    data class Credentials(
        val username: String,
        val password: String,
        val gymId: Int,
    ) {
        fun isValid(): Boolean = username.isNotBlank() && password.isNotBlank() && gymId > 0
    }

    fun loginFormFields(username: String, password: String): Map<String, String> = mapOf(
        "m\$Content\$username" to username,
        "m\$Content\$password" to password,
        // Common Lectio variants
        "username" to username,
        "password" to password,
    )

    fun loginPath(gymId: Int): String = "login.aspx?prevurl=default.aspx"

    fun elevForsidePath(gymId: Int, studentId: String): String =
        "elevforside.aspx?elevid=$studentId"
}
