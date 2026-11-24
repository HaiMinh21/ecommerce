package com.adidas.controller;

import com.adidas.dto.request.UserLoginRequestDTO;
import com.adidas.dto.request.UserRegisterRequestDTO;
import com.adidas.dto.response.AuthResponse;
import org.springframework.http.ResponseEntity;

public interface IAuthController {
    ResponseEntity<String> register(UserRegisterRequestDTO request);
    ResponseEntity<AuthResponse> login(UserLoginRequestDTO request);
}
