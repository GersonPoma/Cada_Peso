package com.presupuesto.transaccionprogramada.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Con la propiedad en false (como en Surefire) no hay generación automática. */
@SpringBootTest(properties = "programadas.generacion.habilitada=false")
class GeneracionDeshabilitadaTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void noExistenLosBeansDeGeneracionAlArrancarNiDiaria() {
        assertThat(contexto.getBeanNamesForType(GeneracionAlArrancar.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(GeneracionDiaria.class)).isEmpty();
    }

    @Test
    void elGeneradorManualSigueDisponible() {
        assertThat(contexto.getBeanNamesForType(GeneradorProgramadas.class)).hasSize(1);
    }
}
