package com.sonograma.config;

import com.sonograma.entity.Usuario;
import com.sonograma.repository.UsuarioRepository;
import com.sonograma.repository.ShippingOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShippingOrderRepository shippingOrderRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int ordenesActualizadas = shippingOrderRepository.marcarImportadasComoRecibidas();
        if (ordenesActualizadas > 0) {
            log.info("{} órdenes importadas fueron marcadas como recibidas", ordenesActualizadas);
        }

        usuarioRepository.findByNombreUsuario("admin").ifPresentOrElse(
            existing -> {
                if (!passwordEncoder.matches("admin123", existing.getContrasenia())) {
                    existing.setContrasenia(passwordEncoder.encode("admin123"));
                    usuarioRepository.save(existing);
                    log.info("Contraseña admin actualizada a admin123");
                }
            },
            () -> {
                Usuario admin = Usuario.builder()
                        .nombreUsuario("admin")
                        .email("admin@sonograma.com")
                        .contrasenia(passwordEncoder.encode("admin123"))
                        .rol("ADMIN")
                        .activo(true)
                        .build();
                usuarioRepository.save(admin);
                log.info("Usuario admin creado: admin / admin123");
            }
        );
    }
}
