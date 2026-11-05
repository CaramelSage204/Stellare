package com.example.stellare.data.repository

import android.util.Log
import com.example.stellare.data.model.AccountDeletionPlan
import com.example.stellare.data.model.AccountDeletionResult
import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.DeletionProgress
import com.example.stellare.data.model.DeletionStep
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.RecentLoginRequiredException
import com.example.stellare.data.model.RecentSignIn
import com.example.stellare.data.model.StepTimeoutException
import com.example.stellare.data.remote.AppointmentDataSource
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.ChatDataSource
import com.example.stellare.data.remote.NoteDataSource
import com.example.stellare.data.remote.PostDataSource
import com.example.stellare.data.remote.PrivateProfileDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.VerificationDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class AccountDeleter(
    private val authDataSource: AuthDataSource,
    private val userDataSource: UserDataSource,
    private val privateProfileDataSource: PrivateProfileDataSource,
    private val postDataSource: PostDataSource,
    private val chatDataSource: ChatDataSource,
    private val noteDataSource: NoteDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val verificationDataSource: VerificationDataSource,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun delete(): AccountDeletionResult {
        val uid = authDataSource.getCurrentUserIdOnce()
            ?: return AccountDeletionResult.Failed(DeletionProgress())
        val fresh = try {
            RecentSignIn.isFresh(authDataSource.lastSignInMillis(), clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nie udalo sie ustalic czasu logowania", e)
            return AccountDeletionResult.Failed(DeletionProgress())
        }
        if (!fresh) return AccountDeletionResult.RecentLoginRequired

        val posts: List<PostModel>
        val notes: List<NoteModel>
        val appointments: List<AppointmentModel>
        val hasVerificationRequest: Boolean
        try {
            posts = bounded { postDataSource.getPostsByAuthor(uid).first() }
            notes = ownedChatNotes(uid)
            appointments = ownedFreeSlots(uid)
            hasVerificationRequest = hasVerificationRequest(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nie udalo sie ustalic, co usunac", e)
            return AccountDeletionResult.Failed(DeletionProgress())
        }

        val plan = AccountDeletionPlan.build(uid, posts, notes, appointments, hasVerificationRequest)
        return execute(uid, plan)
    }

    private suspend fun ownedChatNotes(uid: String): List<NoteModel> {
        val chats = bounded { chatDataSource.getChatsForUser(uid).first() }
        return chats
            .filter { it.psychologistId == uid }
            .flatMap { chat ->
                bounded { noteDataSource.getNotesForChat(chat.chatId).first() }
            }
    }

    private suspend fun ownedFreeSlots(uid: String): List<AppointmentModel> {
        return bounded { appointmentDataSource.getFreeSlotsForPsychologist(uid).first() }
    }

    private suspend fun hasVerificationRequest(uid: String): Boolean {
        return bounded { verificationDataSource.myRequest(uid).first() != null }
    }

    private suspend fun execute(uid: String, plan: List<DeletionStep>): AccountDeletionResult {
        var progress = DeletionProgress(
            notesTotal = plan.count { it is DeletionStep.DeleteNote },
            postsTotal = plan.count { it is DeletionStep.DeletePost },
            slotsTotal = plan.count { it is DeletionStep.DeleteFreeSlot },
            verificationPlanned = DeletionStep.DeleteVerificationRequest in plan
        )
        for (step in plan) {
            try {
                progress = bounded { run(uid, step, progress) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecentLoginRequiredException) {
                return AccountDeletionResult.Failed(progress, authRecentLoginRequired = true)
            } catch (e: StepTimeoutException) {
                Log.e(TAG, "Serwer nie odpowiedzial na krok usuwania konta: $step")
                return AccountDeletionResult.Failed(progress, writeTimedOut = true)
            } catch (e: Exception) {
                Log.e(TAG, "Krok usuwania konta nie powiodl sie: $step", e)
                return AccountDeletionResult.Failed(progress)
            }
        }
        return AccountDeletionResult.Deleted
    }

    /**
     * Dokument prywatny idzie PRZED publicznym. Powodem nie jest uprawnienie (reguła opiera się na
     * uid z tokenu i nie zależy od dokumentu publicznego), tylko okno przerwania: usuwanie to ciąg
     * zapisów bez transakcji, a awaria po skasowaniu dokumentu publicznego zostawiłaby sierocy
     * dokument z numerem telefonu i adresem e-mail, którego nikt już nie wyświetli ani nie posprząta.
     */
    private suspend fun run(uid: String, step: DeletionStep, progress: DeletionProgress): DeletionProgress =
        when (step) {
            is DeletionStep.DeleteNote -> {
                noteDataSource.deleteNote(step.noteId)
                progress.copy(notesDeleted = progress.notesDeleted + 1)
            }
            DeletionStep.DeletePrivateProfile -> {
                privateProfileDataSource.deletePrivateProfile(uid)
                progress.copy(privateProfileDeleted = true)
            }
            DeletionStep.DeleteUserDocument -> {
                userDataSource.deleteUser(uid)
                progress.copy(userDocumentDeleted = true)
            }
            is DeletionStep.DeletePost -> {
                postDataSource.deletePost(step.postId)
                progress.copy(postsDeleted = progress.postsDeleted + 1)
            }
            is DeletionStep.DeleteFreeSlot -> {
                appointmentDataSource.deleteAppointment(step.appointmentId)
                progress.copy(slotsDeleted = progress.slotsDeleted + 1)
            }
            DeletionStep.DeleteVerificationRequest -> {
                verificationDataSource.deleteRequest(uid)
                progress.copy(verificationDeleted = true)
            }
            DeletionStep.DeleteAuthAccount -> {
                authDataSource.deleteCurrentAccount()
                progress
            }
        }

    private suspend fun <T> bounded(block: suspend () -> T): T =
        withTimeoutOrNull(STEP_TIMEOUT_MS) { block() } ?: throw StepTimeoutException()

    private companion object {
        const val TAG = "AccountDeleter"
        const val STEP_TIMEOUT_MS = 20_000L
    }
}
