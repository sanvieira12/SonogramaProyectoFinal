package com.sonograma.service;

import com.sonograma.dto.LoginResponse;
import com.sonograma.entity.Usuario;
import com.sonograma.repository.UsuarioRepository;
import com.sonograma.security.GoogleOAuthAuthenticationException;
import com.sonograma.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthAuthenticationServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private GoogleOAuthAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthAuthenticationService(
                usuarioRepository,
                jwtTokenProvider,
                "sonograma.tiendadediscos@gmail.com");
    }

    @Test
    void approvedVerifiedEmailMapsToExistingAdminAndCreatesJwt() {
        Usuario admin = admin();
        when(usuarioRepository.findByNombreUsuario("admin")).thenReturn(Optional.of(admin));
        when(jwtTokenProvider.generarToken(admin)).thenReturn("jwt-google");

        LoginResponse response = service.authenticate(
                "  SONOGRAMA.TIENDADEDISCOS@GMAIL.COM ", true);

        assertThat(response.getToken()).isEqualTo("jwt-google");
        assertThat(response.getUsuario().getNombreUsuario()).isEqualTo("admin");
        assertThat(response.getUsuario().getRol()).isEqualTo("ADMIN");
        verify(usuarioRepository).save(admin);
    }

    @Test
    void differentGoogleEmailIsForbiddenAndDoesNotCreateAUser() {
        assertForbidden(() -> service.authenticate("otra@gmail.com", true));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unverifiedGoogleEmailIsForbidden() {
        assertForbidden(() -> service.authenticate(
                "sonograma.tiendadediscos@gmail.com", false));
    }

    @Test
    void missingGoogleEmailIsForbidden() {
        assertForbidden(() -> service.authenticate(null, true));
    }

    @Test
    void missingLocalAdministratorFailsWithoutCreatingAnotherAdministrator() {
        when(usuarioRepository.findByNombreUsuario("admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(
                "sonograma.tiendadediscos@gmail.com", true))
                .isInstanceOfSatisfying(GoogleOAuthAuthenticationException.class,
                        exception -> assertThat(exception.getStatus())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(usuarioRepository, never()).save(any());
    }

    private void assertForbidden(Runnable authentication) {
        assertThatThrownBy(authentication::run)
                .isInstanceOfSatisfying(GoogleOAuthAuthenticationException.class,
                        exception -> assertThat(exception.getStatus())
                                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Usuario admin() {
        return Usuario.builder()
                .idUsuario(1L)
                .nombreUsuario("admin")
                .email("admin@sonograma.com")
                .contrasenia("hash")
                .rol("ADMIN")
                .activo(true)
                .build();
    }
}
