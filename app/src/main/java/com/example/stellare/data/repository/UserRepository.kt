package com.example.stellare.data.repository

import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.PrivateProfileDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.FavoriteDataSource
import com.example.stellare.data.remote.RatingDataSource
import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.RatingModel
import com.example.stellare.data.model.NameLookup
import com.example.stellare.data.model.PrivateProfile
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepository(
    private val authDataSource: AuthDataSource,
    private val userDataSource: UserDataSource,
    private val favoriteDataSource: FavoriteDataSource,
    private val ratingDataSource: RatingDataSource,
    private val privateProfileDataSource: PrivateProfileDataSource
) {
    private val registrationInProgress = MutableStateFlow(false)
    private val knownNames = ConcurrentHashMap<String, String>()
    private val ensuredPrivateProfiles = ConcurrentHashMap.newKeySet<String>()

    private val currentUserReloads = MutableStateFlow(0)

    /**
     * Ponowienie siedzi **przy strumieniu**, a nie przy jednym modelu widoku, bo odmowa reguł
     * kończy nasłuch Firestore na zawsze — odżywa dopiero nowy. Gdyby przycisk odświeżał tylko
     * strumień ekranu, bramka nawigacji zostałaby na martwym nasłuchu i konto bez dokumentu
     * zostałoby uwięzione na kanale ogłoszeń, bez drogi na zakładanie profilu.
     */
    fun retryCurrentUser() {
        currentUserReloads.value += 1
    }

    fun getCurrentUserFlow(): Flow<CurrentUserRead> = currentUserReloads.flatMapLatest {
        registrationInProgress.flatMapLatest { busy ->
            if (busy) emptyFlow()
            else authDataSource.currentUserId.flatMapLatest { uid ->
                if (uid != null) userDataSource.getUser(uid)
                else flowOf(CurrentUserRead.SignedOut)
            }
        }
    }

    fun signedInUserIdFlow(): Flow<String?> = authDataSource.currentUserId.distinctUntilChanged()

    fun isSignedInFlow(): Flow<Boolean> = authDataSource.currentUserId
        .map { it != null }
        .distinctUntilChanged()

    fun <T> whileSignedIn(signedOut: T, stream: () -> Flow<T>): Flow<T> = isSignedInFlow()
        .flatMapLatest { signedIn -> if (signedIn) stream() else flowOf(signedOut) }

    suspend fun <T> whileRegistering(block: suspend () -> T): T {
        registrationInProgress.value = true
        try {
            return block()
        } finally {
            registrationInProgress.value = false
        }
    }

    fun holdProfileObservation() {
        ensuredPrivateProfiles.clear()
        registrationInProgress.value = true
    }

    fun releaseProfileObservation() {
        registrationInProgress.value = false
    }

    suspend fun getCurrentUser(): UserModel? =
        authDataSource.getCurrentUserIdOnce()?.let { userDataSource.getUserOnce(it) }

    /**
     * Rozróżnia "nikt nie jest zalogowany" od "odczytu profilu nie udało się wykonać".
     * [getCurrentUser] zwraca `null` tylko w pierwszym przypadku, a w drugim rzuca, więc każda
     * ścieżka zapisu musi ten wyjątek obsłużyć, zanim wyjdzie z korutyny.
     */
    suspend fun readCurrentUser(): CurrentUserRead = try {
        getCurrentUser()?.let(CurrentUserRead::Loaded) ?: CurrentUserRead.SignedOut
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CurrentUserRead.Unavailable(e)
    }

    fun getUsersFlow(role: String, currentUserId: String): Flow<List<UserModel>> =
        userDataSource.getAllByRoleExcluding(role, currentUserId)

    fun getUsersOfRoleFlow(role: UserRole): Flow<List<UserModel>> =
        userDataSource.getAllByRole(role.name)

    suspend fun getUserById(userId: String): UserModel? =
        userDataSource.getUserOnce(userId)

    suspend fun namesOf(ids: Set<String>): Map<String, String> =
        lookupNames(ids).mapNotNull { (id, lookup) -> (lookup as? NameLookup.Known)?.let { id to it.name } }.toMap()

    suspend fun lookupNames(ids: Set<String>): Map<String, NameLookup> {
        val missing = ids.filterNot { knownNames.containsKey(it) }
        val fetched = coroutineScope {
            missing.map { id -> async { id to fetchName(id) } }.awaitAll()
        }.toMap()
        fetched.forEach { (id, lookup) -> if (lookup is NameLookup.Known) knownNames[id] = lookup.name }
        return ids.associateWith { id -> knownNames[id]?.let(NameLookup::Known) ?: fetched.getValue(id) }
    }

    private suspend fun fetchName(userId: String): NameLookup = try {
        userDataSource.getUserOnce(userId)
            ?.let { "${it.firstName} ${it.lastName}".trim().ifEmpty { null } }
            ?.let(NameLookup::Known)
            ?: NameLookup.Absent
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NameLookup.Unavailable
    }

    fun getUserByIdFlow(userId: String): Flow<UserModel?> =
        userDataSource.getUser(userId).map { read -> (read as? CurrentUserRead.Loaded)?.user }

    fun currentPrivateProfileFlow(): Flow<PrivateProfile?> = authDataSource.currentUserId.flatMapLatest { uid ->
        if (uid != null) privateProfileDataSource.getPrivateProfile(uid) else flowOf(null)
    }

    suspend fun currentPrivateProfile(): PrivateProfile? = currentPrivateProfileFlow().first()

    /**
     * Zakłada brakujący dokument prywatny z saldem 0 (konto sprzed przeniesienia pól nie ma portfela).
     * Nie odtwarza salda i nie rusza istniejącego dokumentu.
     */
    suspend fun ensurePrivateProfile(uid: String, email: String) {
        if (!ensuredPrivateProfiles.add(uid)) return
        try {
            privateProfileDataSource.createPrivateProfile(uid, phone = "", email = email)
        } catch (e: Exception) {
            ensuredPrivateProfiles.remove(uid)
            throw e
        }
    }

    /**
     * Dokument prywatny powstaje PRZED publicznym. Profil publiczny jest sygnałem "konto jest
     * kompletne", więc awaria po jego zapisie zostawiłaby konto bez portfela, a awaria po zapisie
     * prywatnego tylko dokument, który ponowienie rejestracji zostawia nietknięty.
     */
    suspend fun registerProfile(user: UserModel, phone: String, email: String) {
        privateProfileDataSource.createPrivateProfile(user.id, phone, email)
        userDataSource.saveUser(user)
    }

    suspend fun updateUser(user: UserModel) =
        userDataSource.saveUser(user)

    suspend fun savePhone(uid: String, phone: String) =
        privateProfileDataSource.savePhone(uid, phone)

    fun getRatingsFlow(): Flow<Map<String, RatingModel>> =
        ratingDataSource.getRatings()

    fun getRatingFlow(psychologistId: String): Flow<RatingModel> =
        ratingDataSource.getRating(psychologistId)

    fun getFavoritesForUserFlow(userId: String) =
        favoriteDataSource.getFavoritesForUser(userId)

    suspend fun insertFavorite(userId: String, targetId: String) =
        favoriteDataSource.saveFavorite(FavoriteModel(userId, targetId))

    suspend fun deleteFavorite(userId: String, targetId: String) =
        favoriteDataSource.deleteFavorite(userId, targetId)
}