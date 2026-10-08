package com.presupuesto.transaccionprogramada.service;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Una vez al día genera las ocurrencias vencidas de todos los presupuestos. La hora se configura
 * con {@code programadas.generacion.cron} (por defecto, las 03:00 UTC). Se desactiva con
 * {@code programadas.generacion.habilitada=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "programadas.generacion.habilitada", havingValue = "true", matchIfMissing = true)
class GeneracionDiaria {

    private final GeneradorProgramadas generador;
    private final Clock clock;

    @Scheduled(cron = "${programadas.generacion.cron:0 0 3 * * *}", zone = "UTC")
    void ejecutar() {
        try {
            ResultadoGeneracion resultado = generador.generarVencidas(LocalDate.now(clock));
            log.info("Transacciones programadas diarias: {} generadas, {} con error",
                    resultado.generadas(), resultado.plantillasConError());
        } catch (RuntimeException fallo) {
            log.error("No se pudieron generar las transacciones programadas del día", fallo);
        }
    }
}
