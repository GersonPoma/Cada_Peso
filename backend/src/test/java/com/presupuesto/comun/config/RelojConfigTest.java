package com.presupuesto.comun.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(RelojDePruebaConfig.class)
class RelojConfigTest {

    @Autowired
    private Clock clock;

    @Autowired
    private RelojDePrueba relojDePrueba;

    @AfterEach
    void reiniciarReloj() {
        relojDePrueba.reiniciar();
    }

    @Test
    void elClockInyectadoDevuelveElInstanteFijadoEnElRelojDePrueba() {
        Instant instante = Instant.parse("2030-01-15T08:30:00Z");

        relojDePrueba.fijar(instante);

        assertThat(clock.instant()).isEqualTo(instante);
    }
}
