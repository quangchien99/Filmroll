package com.filmroll.camera.notification

/** Once-a-day nudge to come back and develop another shot. */
expect object DailyReminder {

    /**
     * Turns the daily reminder on or off.
     *
     * @return true when the reminder is scheduled *and* the OS will let us post it. A false
     *   result for [enabled] = true means the user has to grant notification access first.
     */
    suspend fun setEnabled(enabled: Boolean): Boolean
}
