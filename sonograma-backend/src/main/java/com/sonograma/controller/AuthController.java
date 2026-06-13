package com.sonograma.controller;

import com.sonograma.dto.LoginRequest;
import com.sonograma.dto.LoginResponse;
import com.sonograma.dto.RegistroRequest;
import com.sonograma.dto.UsuarioDTO;
import com.sonograma.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/registro")
    public ResponseEntity<UsuarioDTO> registro(@RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authenticationService.registrar(
                        request.getNombreUsuario(),
                        request.getEmail(),
                        request.getContrasenia()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(
                request.getNombreUsuario(),
                request.getContrasenia()));
    }

    @GetMapping("/session")
    public ResponseEntity<UsuarioDTO> session(Principal principal) {
        return ResponseEntity.ok(authenticationService.obtenerUsuarioActual(principal.getName()));
    }
}
