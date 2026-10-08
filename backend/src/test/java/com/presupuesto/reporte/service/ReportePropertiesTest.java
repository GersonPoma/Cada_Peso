package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportePropertiesTest {

    @EnableConfigurationProperties(ReporteProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void sinConfiguracionElMaximoEsSesentaMeses() {
        runner.run(contexto ->
                assertThat(contexto.getBean(ReporteProperties.class).maxMeses()).isEqualTo(60));
    }

    @Test
    void tomaElValorConfigurado() {
        runner.withPropertyValues("reportes.max-meses=12").run(contexto ->
                assertThat(contexto.getBean(ReporteProperties.class).maxMeses()).isEqualTo(12));
    }

    @Test
    void unMaximoMenorQueUnoNoArranca() {
        runner.withPropertyValues("reportes.max-meses=0")
                .run(contexto -> assertThat(contexto).hasFailed());
        assertThatThrownBy(() -> new ReporteProperties(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
