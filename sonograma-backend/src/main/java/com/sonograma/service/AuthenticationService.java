package com.sonograma.service;

import com.sonograma.dto.LoginResponse;
import com.sonograma.dto.UsuarioDTO;
import com.sonograma.entity.Usuario;
import com.sonograma.repository.UsuarioRepository;
import com.sonograma.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UsuarioDTO registrar(String nombreUsuario, String email, String contrasenia) {
        if (usuarioRepository.findByNombreUsuario(nombreUsuario).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso: " + nombreUsuario);
        }
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("El email ya está registrado: " + email);
        }

        Usuario usuario = Usuario.builder()
                .nombreUsuario(nombreUsuario)
                .email(email)
                .contrasenia(passwordEncoder.encode(contrasenia))
                .rol("OPERADOR")
                .activo(true)
                .build();

        return mapearADTO(usuarioRepository.save(usuario));
    }

    public LoginResponse login(String nombreUsuario, String contrasenia) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario o contraseña incorrectos"));

        if (!usuario.getActivo()) {
            throw new IllegalArgumentException("Usuario desactivado");
        }

        if (!passwordEncoder.matches(contrasenia, usuario.getContrasenia())) {
            throw new IllegalArgumentException("Usuario o contraseña incorrectos");
        }

        usuario.setFechaUltimoAcceso(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtTokenProvider.generarToken(usuario);
        return LoginResponse.builder()
                .token(token)
                .usuario(mapearADTO(usuario))
                .build();
    }

    private UsuarioDTO mapearADTO(Usuario usuario) {
        return UsuarioDTO.builder()
                .idUsuario(usuario.getIdUsuario())
                .nombreUsuario(usuario.getNombreUsuario())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .activo(usuario.getActivo())
                .build();
    }
}
