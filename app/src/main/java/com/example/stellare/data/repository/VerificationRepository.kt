package com.example.stellare.data.repository

import com.example.stellare.data.model.VerificationRequestModel
import com.example.stellare.data.remote.VerificationDataSource
import kotlinx.coroutines.flow.Flow

class VerificationRepository(private val verificationDataSource: VerificationDataSource) {

    fun myRequest(uid: String): Flow<VerificationRequestModel?> =
        verificationDataSource.myRequest(uid)

    fun pendingRequests(): Flow<List<VerificationRequestModel>> =
        verificationDataSource.pendingRequests()

    suspend fun submit(request: VerificationRequestModel) =
        verificationDataSource.submit(request)

    suspend fun decide(uid: String, approved: Boolean, note: String) =
        verificationDataSource.decide(uid, approved, note)
}
