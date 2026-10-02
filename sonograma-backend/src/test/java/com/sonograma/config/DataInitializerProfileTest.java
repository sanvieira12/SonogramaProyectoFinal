package com.sonograma.config;

import com.sonograma.repository.ShippingOrderRepository;
import com.sonograma.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;

class DataInitializerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UsuarioRepository.class, () -> mock(UsuarioRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(ShippingOrderRepository.class, () -> mock(ShippingOrderRepository.class))
            .withUserConfiguration(DataInitializer.class);

    @Test
    void productionProfileDoesNotRegisterMutationInitializer() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> context.assertThat().doesNotHaveBean(DataInitializer.class));
    }

    @Test
    void developmentProfileKeepsLocalInitializerAvailable() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> context.assertThat().hasSingleBean(DataInitializer.class));
    }
}
