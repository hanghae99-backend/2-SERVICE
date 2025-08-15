package kr.hhplus.be.server.domain.reservation.model

import com.fasterxml.jackson.annotation.JsonCreator
import jakarta.persistence.*
import com.fasterxml.jackson.annotation.JsonProperty
import kr.hhplus.be.server.global.common.BaseEntity

@Entity
@Table(name = "reservation_status_type")
class ReservationStatusType @JsonCreator constructor(
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