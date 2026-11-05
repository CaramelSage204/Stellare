package com.example.stellare.data.remote

object FirestoreCollections {
    const val RATINGS = "ratings"
    const val REVIEWS = "reviews"
    const val APPOINTMENTS = "appointments"
    const val ESCROWS = "escrows"
    const val USERS = "users"
    const val WALLET_TRANSACTIONS = "wallet_transactions"
    const val COMPLETIONS = "completions"
    const val PRIVATE = "private"
    const val CONTACT = "contact"

    fun privateOf(userId: String) = "$USERS/$userId/$PRIVATE"
}
