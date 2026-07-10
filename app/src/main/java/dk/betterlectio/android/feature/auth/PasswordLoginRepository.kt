package dk.betterlectio.android.feature.auth

import dk.betterlectio.android.core.lectio.LectioClient
import dk.betterlectio.android.core.lectio.auth.AuthSessionInstaller
import dk.betterlectio.android.core.lectio.model.FetchPriority
import dk.betterlectio.android.core.lectio.scrape.AspNetForm
import dk.betterlectio.android.core.model.School
import dk.betterlectio.android.core.model.Student
import dk.betterlectio.android.core.result.AppError
import dk.betterlectio.android.core.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordLoginRepository @Inject constructor(
    private val client: LectioClient,
    private val auth: AuthSessionInstaller,
) {
    /**
     * Username/password Lectio login (Flutter parity). Demo school accepts any non-empty creds.
     */
    suspend fun login(school: School, username: String, password: String): AppResult<Student> {
        val creds = PasswordLogin.Credentials(username.trim(), password, school.id)
        if (!creds.isValid()) {
            return AppResult.Failure(AppError.Unknown("Brugernavn og adgangskode er påkrævet"))
        }
        if (school.isDemo || school.id == School.Demo.id) {
            auth.enterDemo()
            return AppResult.Success(Student.Demo)
        }

        val path = PasswordLogin.loginPath(school.id)
        return when (val page = client.get(path, FetchPriority.Important, gymId = school.id)) {
            is AppResult.Failure -> page
            is AppResult.Success -> {
                val fields = AspNetForm.parseAllFormFields(page.data.body).toMap().toMutableMap()
                fields.putAll(PasswordLogin.loginFormFields(creds.username, creds.password))
                fields["__EVENTTARGET"] = "m\$Content\$submitbtn"
                fields["__EVENTARGUMENT"] = ""
                when (
                    val post = client.postForm(
                        path,
                        fields,
                        FetchPriority.Important,
                        gymId = school.id,
                    )
                ) {
                    is AppResult.Failure -> {
                        // Still try identity install if cookies landed
                        when (val complete = auth.completeLoginFromWebView(school)) {
                            is AppResult.Success -> complete
                            is AppResult.Failure -> post
                        }
                    }
                    is AppResult.Success -> {
                        // Prefer cookie-manager extract after browser-like flow; fall back to demo failure
                        when (val complete = auth.completeLoginFromWebView(school)) {
                            is AppResult.Success -> complete
                            is AppResult.Failure ->
                                AppResult.Failure(
                                    AppError.Unknown(
                                        "Login post sendt, men session kunne ikke bekræftes. Prøv MitID.",
                                    ),
                                )
                        }
                    }
                }
            }
        }
    }
}
