package com.presupuesto.comun.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reemplaza el {@code Clock} de la aplicación por un {@link RelojDePrueba} modificable. */
@TestConfiguration
public class RelojDePruebaConfig {

    @Bean
    @Primary
    public RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }
}
