package kr.hhplus.be.server.domain.balance.models

import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonCreator
import kr.hhplus.be.server.domain.concert.models.SeatStatusType
import kr.hhplus.be.server.global.common.BaseEntity
import java.time.LocalDateTime

@Entity
@Table(name = "point_history_type")
class PointHistoryType @JsonCreator constructor(
    @JsonProperty("code")
    @Id
    @Column(name = "code", length = 50)
    var code: String,

    @JsonProperty("name")
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @JsonProperty("description")
    @Column(name = "description", length = 255)
    var description: String? = null,

    @JsonProperty("isActive")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
): BaseEntity()