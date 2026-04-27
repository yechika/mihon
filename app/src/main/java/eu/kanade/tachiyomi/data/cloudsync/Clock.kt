package eu.kanade.tachiyomi.data.cloudsync

fun interface Clock {

    fun nowMillis(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
