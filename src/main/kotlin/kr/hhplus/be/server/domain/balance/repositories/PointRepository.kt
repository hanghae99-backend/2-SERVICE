package kr.hhplus.be.server.domain.balance.repositories

import kr.hhplus.be.server.domain.balance.models.Point

interface PointRepository {
    fun save(point: Point): Point
    fun findById(id: Long): Point?
    fun findByUserId(userId: Long): Point?
    fun delete(point: Point)
    fun deleteAll() // 테스트용 - 모든 포인트 데이터 삭제
}
