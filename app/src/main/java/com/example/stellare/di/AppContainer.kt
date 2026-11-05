package com.example.stellare.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.FirebaseAuthDataSource
import com.example.stellare.data.remote.FirestoreAppointmentDataSource
import com.example.stellare.data.remote.FirestoreEscrowDataSource
import com.example.stellare.data.remote.FirestoreChatDataSource
import com.example.stellare.data.remote.FirestoreFavoriteDataSource
import com.example.stellare.data.remote.FirestoreMessageDataSource
import com.example.stellare.data.remote.FirestoreNoteDataSource
import com.example.stellare.data.remote.FirestorePostDataSource
import com.example.stellare.data.remote.FirestorePrivateProfileDataSource
import com.example.stellare.data.remote.FirestoreRatingDataSource
import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.remote.FirestoreUserDataSource
import com.example.stellare.data.remote.FirestoreVerificationDataSource
import com.example.stellare.data.remote.FirestoreWalletTransactionDataSource
import com.example.stellare.data.repository.AccountDeleter
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.PostRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.VerificationRepository
import com.example.stellare.data.repository.WalletRepository
import com.example.stellare.data.settings.SettingsStore
import com.example.stellare.ui.auth.AuthViewModel
import com.example.stellare.ui.calendar.CalendarViewModel
import com.example.stellare.ui.chat.ChatViewModel
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.posts.PostViewModel
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.settings.AccountDeletionViewModel
import com.example.stellare.ui.settings.SettingsViewModel
import com.example.stellare.ui.verification.VerificationViewModel
import com.example.stellare.ui.wallet.WalletViewModel
import java.util.TimeZone

class AppContainer(val settingsStore: SettingsStore) {
    val authDataSource: AuthDataSource = FirebaseAuthDataSource()

    val userRepository = UserRepository(
        authDataSource,
        FirestoreUserDataSource(),
        FirestoreFavoriteDataSource(),
        FirestoreRatingDataSource(),
        FirestorePrivateProfileDataSource()
    )
    val chatRepository = ChatRepository(
        FirestoreChatDataSource(),
        FirestoreMessageDataSource(),
        FirestoreNoteDataSource()
    )
    val appointmentRepository = AppointmentRepository(FirestoreAppointmentDataSource(), FirestoreEscrowDataSource())
    val reviewRepository = ReviewRepository(FirestoreReviewDataSource())
    val walletRepository = WalletRepository(FirestoreWalletTransactionDataSource())
    val postRepository = PostRepository(FirestorePostDataSource())
    val verificationRepository = VerificationRepository(FirestoreVerificationDataSource())
    val accountDeleter = AccountDeleter(
        authDataSource,
        FirestoreUserDataSource(),
        FirestorePrivateProfileDataSource(),
        FirestorePostDataSource(),
        FirestoreChatDataSource(),
        FirestoreNoteDataSource(),
        FirestoreAppointmentDataSource(),
        FirestoreVerificationDataSource()
    )
}

fun stellareViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { FeedViewModel(container.userRepository) }
    initializer {
        CalendarViewModel(
            container.appointmentRepository,
            container.userRepository,
            TimeZone::getDefault
        )
    }
    initializer { WalletViewModel(container.walletRepository, container.userRepository) }
    initializer { ChatViewModel(container.chatRepository, container.userRepository) }
    initializer {
        ProfileViewModel(container.userRepository, container.reviewRepository, container.postRepository)
    }
    initializer { AuthViewModel(container.userRepository, container.authDataSource) }
    initializer { SettingsViewModel(container.settingsStore) }
    initializer {
        AccountDeletionViewModel(container.accountDeleter, container.userRepository, container.authDataSource)
    }
    initializer { PostViewModel(container.postRepository, container.userRepository) }
    initializer {
        VerificationViewModel(container.verificationRepository, container.userRepository)
    }
}
