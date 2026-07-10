package dk.betterlectio.android.feature.absence

import dk.betterlectio.android.feature.demo.DemoData
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory demo absence registrations. [updateCause] mutates so reloads show the new cause.
 */
@Singleton
class DemoAbsenceState @Inject constructor() {
    private val registrations =
        CopyOnWriteArrayList(DemoData.absence.registrations)

    /** Test / custom seed. */
    fun reset(initial: List<AbsenceRegistration>) {
        registrations.clear()
        registrations.addAll(initial)
    }

    fun overview(): AbsenceOverview = AbsenceOverview(
        teams = DemoData.absence.teams,
        registrations = registrations.toList(),
    )

    fun registrations(): List<AbsenceRegistration> = registrations.toList()

    /**
     * @return true if a registration was updated
     */
    fun updateCause(registrationId: String, cause: String): Boolean {
        val idx = registrations.indexOfFirst { it.id == registrationId }
        if (idx < 0) return false
        registrations[idx] = registrations[idx].copy(cause = cause)
        return true
    }
}
