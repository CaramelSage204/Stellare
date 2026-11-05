package com.example.stellare.data.model

sealed interface DeletionStep {
    data class DeleteNote(val noteId: String) : DeletionStep
    data class DeleteFreeSlot(val appointmentId: String) : DeletionStep
    data object DeletePrivateProfile : DeletionStep
    data object DeleteUserDocument : DeletionStep
    data class DeletePost(val postId: String) : DeletionStep
    data object DeleteVerificationRequest : DeletionStep
    data object DeleteAuthAccount : DeletionStep
}

object AccountDeletionPlan {

    fun build(
        uid: String,
        posts: List<PostModel>,
        notes: List<NoteModel>,
        appointments: List<AppointmentModel> = emptyList(),
        hasVerificationRequest: Boolean = false
    ): List<DeletionStep> {
        val ownNotes = notes.filter { it.authorId == uid || it.authorId.isEmpty() }
        val ownPosts = posts.filter { it.authorId == uid }
        val ownFreeSlots = appointments.filter { it.psychologistId == uid && it.status == FREE_STATUS }
        return ownNotes.map { DeletionStep.DeleteNote(it.noteId) } +
            ownFreeSlots.map { DeletionStep.DeleteFreeSlot(it.appointmentId) } +
            DeletionStep.DeletePrivateProfile +
            DeletionStep.DeleteUserDocument +
            ownPosts.map { DeletionStep.DeletePost(it.id) } +
            listOfNotNull(DeletionStep.DeleteVerificationRequest.takeIf { hasVerificationRequest }) +
            DeletionStep.DeleteAuthAccount
    }

    private const val FREE_STATUS = "FREE"
}

data class DeletionProgress(
    val notesDeleted: Int = 0,
    val notesTotal: Int = 0,
    val privateProfileDeleted: Boolean = false,
    val userDocumentDeleted: Boolean = false,
    val postsDeleted: Int = 0,
    val postsTotal: Int = 0,
    val slotsDeleted: Int = 0,
    val slotsTotal: Int = 0,
    val verificationPlanned: Boolean = false,
    val verificationDeleted: Boolean = false
) {
    val anythingDeleted: Boolean
        get() = notesDeleted > 0 || privateProfileDeleted || userDocumentDeleted || postsDeleted > 0 ||
            slotsDeleted > 0 || verificationDeleted
}

sealed interface AccountDeletionResult {
    data object Deleted : AccountDeletionResult
    data object RecentLoginRequired : AccountDeletionResult
    data class Failed(
        val progress: DeletionProgress,
        val authRecentLoginRequired: Boolean = false,
        val writeTimedOut: Boolean = false
    ) : AccountDeletionResult
}

class RecentLoginRequiredException : Exception()

class StepTimeoutException : Exception()

object RecentSignIn {
    const val WINDOW_MILLIS = 4 * 60 * 1000L

    fun isFresh(lastSignInMillis: Long?, nowMillis: Long): Boolean =
        lastSignInMillis != null && nowMillis - lastSignInMillis <= WINDOW_MILLIS
}
