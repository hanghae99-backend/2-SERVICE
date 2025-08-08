package kr.hhplus.be.server.domain.user.repository

import kr.hhplus.be.server.domain.user.model.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun existsById(id: Long): Boolean
    fun findAll(): List<User>
    fun delete(user: User)
    fun deleteAll() // 테스트용 - 모든 사용자 데이터 삭제
    fun flush() // 변경사항을 즉시 DB에 반영
}
