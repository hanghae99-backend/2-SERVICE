package kr.hhplus.be.server.global.extension

inline fun <T> T?.orElseThrow(lazyMessage: () -> Throwable): T {
    return this ?: throw lazyMessage()
}
