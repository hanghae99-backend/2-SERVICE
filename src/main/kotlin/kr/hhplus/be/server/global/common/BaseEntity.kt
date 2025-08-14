package kr.hhplus.be.server.global.common

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    
    @JsonIgnore
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    var createdAt: LocalDateTime? = LocalDateTime.now()

    @JsonIgnore
    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = LocalDateTime.now()
}
