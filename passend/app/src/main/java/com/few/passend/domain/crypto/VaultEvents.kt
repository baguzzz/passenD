package com.few.passend.domain.crypto

object VaultEvents {
    private val lockListeners = mutableListOf<() -> Unit>()

    fun onLock(cb: () -> Unit) { synchronized(lockListeners) { lockListeners.add(cb) } }
    fun offLock(cb: () -> Unit) { synchronized(lockListeners) { lockListeners.remove(cb) } }

    fun notifyLocked() {
        val snapshot = synchronized(lockListeners) { lockListeners.toList() }
        snapshot.forEach { try { it() } catch (_: Throwable) {} }
    }
}