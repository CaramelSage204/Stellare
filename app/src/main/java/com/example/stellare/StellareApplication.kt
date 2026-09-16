package com.example.stellare

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StellareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            connectToLocalEmulators()
        }
    }

    private fun connectToLocalEmulators() {
        FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_PORT)
        Log.w(
            "Stellare",
            "Firebase wskazuje na LOKALNE emulatory $EMULATOR_HOST:$FIRESTORE_PORT / $AUTH_PORT"
        )
    }

    private companion object {
        const val EMULATOR_HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
    }
}
