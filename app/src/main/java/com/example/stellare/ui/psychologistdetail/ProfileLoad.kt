package com.example.stellare.ui.psychologistdetail

import com.example.stellare.data.model.UserModel

sealed interface ProfileLoad {
    data object Loading : ProfileLoad
    data class Loaded(val user: UserModel) : ProfileLoad
    data object Absent : ProfileLoad
    data class Failed(val message: String) : ProfileLoad
}
