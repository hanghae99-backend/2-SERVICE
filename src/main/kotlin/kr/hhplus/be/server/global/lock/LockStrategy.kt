package kr.hhplus.be.server.global.lock

enum class LockStrategy {
    SIMPLE,
    SPIN,
    PUB_SUB
}
