package com.presupuesto.transaccionprogramada.service;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Al arrancar genera las ocurrencias vencidas de todos los presupuestos (las que se perdieron
 * con la aplicación apagada). Un fallo se registra y no tumba el arranque. Se desactiva con
 * {@code programadas.generacion.habilitada=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "programadas.generacion.habilitada", havingValue = "true", matchIfMissing = true)
class GeneracionAlArrancar implements ApplicationRunner {

    private final GeneradorProgramadas generador;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ResultadoGeneracion resultado = generador.generarVencidas(LocalDate.now(clock));
            log.info("Transacciones programadas al arrancar: {} generadas, {} con error",
                    resultado.generadas(), resultado.plantillasConError());
        } catch (RuntimeException fallo) {
            log.error("No se pudieron generar las transacciones programadas al arrancar", fallo);
        }
    }
}
