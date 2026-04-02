package cn.aslight.workhub.common.api;

/**
 * 统一 API 响应模型。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "OK", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(0, "OK", null);
    }

    public static ApiResponse<Void> failure(String message) {
        return new ApiResponse<>(1, message, null);
    }
}
