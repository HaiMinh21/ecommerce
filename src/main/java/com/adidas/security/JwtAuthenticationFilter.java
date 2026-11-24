package com.adidas.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityErrorHandler errorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/api/v1/auth/login".equals(path) || "/api/v1/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String jwt = authHeader.substring(7).trim();
            String username = jwtService.extractUsername(jwt);
            if (username == null || username.isBlank()) {
                throw new BadCredentialsException("Missing token subject");
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!jwtService.isTokenValid(jwt, userDetails) || !userDetails.isEnabled()
                        || !userDetails.isAccountNonLocked() || !userDetails.isAccountNonExpired()
                        || !userDetails.isCredentialsNonExpired()) {
                    throw new BadCredentialsException("Invalid token or unavailable account");
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();
            errorHandler.unauthorized(request, response, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            return;
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errorHandler.unauthorized(request, response, "Token không hợp lệ hoặc tài khoản không khả dụng. Vui lòng đăng nhập lại.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
