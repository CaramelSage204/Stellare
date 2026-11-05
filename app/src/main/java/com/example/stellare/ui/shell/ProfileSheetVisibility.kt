package com.example.stellare.ui.shell

import com.example.stellare.ui.auth.AuthStatus

object ProfileSheetVisibility {
    fun mayStayOpen(status: AuthStatus): Boolean = status != AuthStatus.PROFILE_INCOMPLETE
}
