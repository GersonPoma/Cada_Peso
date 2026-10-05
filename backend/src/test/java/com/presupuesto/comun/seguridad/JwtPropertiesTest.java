package com.presupuesto.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class JwtPropertiesTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void laExpiracionPorDefectoEsDe24Horas() {
        assertThat(jwtProperties.expiracion()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void elSecretoPorDefectoTieneAlMenos32Bytes() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "JWT_SECRET está definido en el entorno");

        assertThat(jwtProperties.secreto().getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThanOrEqualTo(32);
    }

    @Test
    void elPasswordEncoderCodificaYVerificaUnaContrasena() {
        String hash = passwordEncoder.encode("secreta123");

        assertThat(hash).isNotEqualTo("secreta123");
        assertThat(passwordEncoder.matches("secreta123", hash)).isTrue();
        assertThat(passwordEncoder.matches("otra-clave", hash)).isFalse();
    }
}
