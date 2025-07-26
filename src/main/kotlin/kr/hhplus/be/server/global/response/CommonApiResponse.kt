package kr.hhplus.be.server.global.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 API 응답")
data class CommonApiResponse<T>(
    @Schema(description = "성공 여부", example = "true")
    val success: Boolean,
    
    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다")
    val message: String,
    
    @Schema(description = "응답 데이터")
    val data: T? = null,
    
    @Schema(description = "에러 코드", example = "USER_NOT_FOUND")
    val errorCode: String? = null
) {
    companion object {
        fun <T> success(data: T, message: String = "성공"): CommonApiResponse<T> {
            return CommonApiResponse(
                success = true,
                message = message,
                data = data
            )
        }
        
        fun <T> success(message: String = "성공"): CommonApiResponse<T> {
            return CommonApiResponse(
                success = true,
                message = message,
                data = null
            )
        }
        
        fun <T> error(message: String, errorCode: String? = null): CommonApiResponse<T> {
            return CommonApiResponse(
                success = false,
                message = message,
                errorCode = errorCode
            )
        }
    }
}
