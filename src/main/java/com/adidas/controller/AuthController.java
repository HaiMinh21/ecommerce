package com.adidas.controller;

import com.adidas.dto.request.UserLoginRequestDTO;
import com.adidas.dto.request.UserRegisterRequestDTO;
import com.adidas.dto.response.AuthResponse;
import com.adidas.entity.Roles;
import com.adidas.entity.UserStatus;
import com.adidas.entity.Users;
import com.adidas.exception.BadRequestException;
import com.adidas.exception.ResourceNotFoundException;
import com.adidas.repository.IRoleRepository;
import com.adidas.repository.IUserRepository;
import com.adidas.security.CustomUserDetails;
import com.adidas.security.JwtService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements IAuthController{

    private final AuthenticationManager authenticationManager;
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegisterRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username đã tồn tại!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng!");
        }

        // Tìm Role mặc định cho khách hàng (kiểm tra tên trong bảng roles của bạn: USER hay CUSTOMER)
        Roles defaultRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền mặc định CUSTOMER"));

        Users newUser = new Users();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setPhone(request.getPhone());
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setRole(defaultRole);

        userRepository.save(newUser);
        return ResponseEntity.ok("Đăng ký tài khoản thành công!");
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody UserLoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(userDetails.getUser().getId())
                .username(userDetails.getUsername())
                .role(userDetails.getUser().getRole().getName())
                .build());
    }
}
