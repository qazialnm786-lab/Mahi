package com.example.session

sealed class SessionState {
    data object Disconnected : SessionState()
    data object Connecting : SessionState()
    data object Listening : SessionState()
    data object Speaking : SessionState()
    data class Error(val message: String) : SessionState()
}
