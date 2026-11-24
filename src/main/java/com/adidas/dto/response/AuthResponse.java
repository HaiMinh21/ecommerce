package com.adidas.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private Long userId;
    private String username;
    private String role;
}
