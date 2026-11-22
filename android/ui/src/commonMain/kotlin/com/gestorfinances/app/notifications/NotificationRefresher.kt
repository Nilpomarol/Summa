package com.gestorfinances.app.notifications

interface NotificationRefresher {
    suspend fun refreshNotifications()

    companion object {
        val NoOp: NotificationRefresher = object : NotificationRefresher {
            override suspend fun refreshNotifications() = Unit
        }
    }
}
