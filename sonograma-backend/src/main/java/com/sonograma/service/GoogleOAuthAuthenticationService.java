package com.sonograma.service;

import com.sonograma.dto.LoginResponse;
import com.sonograma.dto.UsuarioDTO;
import com.sonograma.entity.Usuario;
import com.sonograma.repository.UsuarioRepository;
import com.sonograma.security.GoogleOAuthAuthenticationException;
import com.sonograma.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class GoogleOAuthAuthenticationService {

    private static final String LOCAL_ADMIN_USERNAME = "admin";

    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final String approvedEmail;

    public GoogleOAuthAuthenticationService(
            UsuarioRepository usuarioRepository,
            JwtTokenProvider jwtTokenProvider,
            @Value("${sonograma.google.admin-email}") String approvedEmail) {
        this.usuarioRepository = usuarioRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.approvedEmail = normalizeEmail(approvedEmail);
    }

    @Transactional
    public LoginResponse authenticate(String email, boolean emailVerified) {
        if (!StringUtils.hasText(email)) {
            throw forbidden("Google no proporcionó un email para esta cuenta.");
        }
        if (!emailVerified) {
            throw forbidden("Google no confirmó el email de esta cuenta.");
        }
        if (!normalizeEmail(email).equals(approvedEmail)) {
            throw forbidden("Esta cuenta de Google no está autorizada para ingresar a Sonograma.");
        }

        Usuario admin = usuarioRepository.findByNombreUsuario(LOCAL_ADMIN_USERNAME)
                .orElseThrow(() -> unavailable("No se pudo cargar el administrador de Sonograma."));
        if (!Boolean.TRUE.equals(admin.getActivo())) {
            throw unavailable("El administrador de Sonograma no está disponible.");
        }

        admin.setFechaUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(admin);

        String token = jwtTokenProvider.generarToken(admin);
        return LoginResponse.builder()
                .token(token)
                .usuario(UsuarioDTO.builder()
                        .idUsuario(admin.getIdUsuario())
                        .nombreUsuario(admin.getNombreUsuario())
                        .email(admin.getEmail())
                        .rol(admin.getRol())
                        .activo(admin.getActivo())
                        .build())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private GoogleOAuthAuthenticationException forbidden(String message) {
        return new GoogleOAuthAuthenticationException(HttpStatus.FORBIDDEN, message);
    }

    private GoogleOAuthAuthenticationException unavailable(String message) {
        return new GoogleOAuthAuthenticationException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
