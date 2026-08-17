package com.example.smartcanteen.http;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(40000, message, null);
    }
}
