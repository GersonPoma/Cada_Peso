package com.presupuesto.transaccionprogramada.service;

import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.service.TransaccionService;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import com.presupuesto.transaccionprogramada.repository.TransaccionProgramadaRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crea las transacciones reales de las ocurrencias vencidas de las plantillas activas. Cada
 * plantilla va en su propia transacción de base de datos (con su fila bloqueada): un fallo la
 * revierte entera, queda anotado en {@code ultimoError} y no impide procesar las demás. Es
 * idempotente: la ocurrencia generada queda registrada (plantilla y fecha, únicas juntas) y
 * {@code proximaFecha} avanza, así que repetir la ejecución no crea nada.
 *
 * <p>No es transaccional en conjunto, y el guardado de {@code ultimoError} usa su propio
 * {@link TransactionTemplate} (no una auto-invocación, que el proxy no interceptaría).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneradorProgramadas {

    /** Máximo de ocurrencias por plantilla y ejecución; el resto va en la siguiente. */
    public static final int TOPE_POR_EJECUCION = 366;

    static final String MENSAJE_ERROR_INESPERADO = "Error inesperado al generar la transacción";

    private final TransaccionProgramadaRepository programadaRepository;
    private final TransaccionRepository transaccionRepository;
    private final TransaccionService transaccionService;
    private final PlatformTransactionManager transactionManager;

    /** Todos los presupuestos: lo usan el arranque y la tarea diaria. */
    public ResultadoGeneracion generarVencidas(LocalDate hasta) {
        return procesar(programadaRepository.idsVencidas(hasta), hasta);
    }

    /** Solo las plantillas del presupuesto indicado. */
    public ResultadoGeneracion generarVencidas(Long presupuestoId, LocalDate hasta) {
        return procesar(
                programadaRepository.idsVencidasDelPresupuesto(presupuestoId, hasta), hasta);
    }

    private ResultadoGeneracion procesar(List<Long> ids, LocalDate hasta) {
        TransactionTemplate porPlantilla = new TransactionTemplate(transactionManager);
        int generadas = 0;
        int conError = 0;
        for (Long id : ids) {
            try {
                Integer creadas = porPlantilla.execute(estado -> generarDe(id, hasta));
                generadas += creadas == null ? 0 : creadas;
            } catch (RuntimeException fallo) {
                conError++;
                log.error("No se pudo generar la transacción programada {}", id, fallo);
                registrarError(id, fallo);
            }
        }
        return new ResultadoGeneracion(generadas, conError);
    }

    /** Dentro de la transacción de la plantilla: devuelve cuántas transacciones creó. */
    private int generarDe(Long id, LocalDate hasta) {
        TransaccionProgramada programada = programadaRepository.bloquear(id).orElse(null);
        if (programada == null || !programada.isActiva()
                || programada.getProximaFecha() == null
                || programada.getProximaFecha().isAfter(hasta)) {
            return 0;
        }
        int creadas = 0;
        int procesadas = 0;
        LocalDate ultima = null;
        LocalDate ocurrencia = programada.getProximaFecha();
        while (ocurrencia != null && !ocurrencia.isAfter(hasta)
                && procesadas < TOPE_POR_EJECUCION) {
            if (!transaccionRepository.existsByProgramadaIdAndFechaOcurrencia(id, ocurrencia)) {
                transaccionService.crearProgramada(
                        programada.getPresupuesto(), solicitud(programada, ocurrencia), id,
                        ocurrencia);
                creadas++;
            }
            procesadas++;
            ultima = ocurrencia;
            ocurrencia = siguiente(programada, ocurrencia);
        }
        programada.registrarGeneracion(ultima, ocurrencia);
        programadaRepository.saveAndFlush(programada);
        return creadas;
    }

    /** Siguiente ocurrencia desde el inicio (nunca sumando un periodo a la anterior). */
    static LocalDate siguiente(TransaccionProgramada programada, LocalDate ocurrencia) {
        LocalDate siguiente = CalendarioProgramado.primeraDespuesDe(
                programada.getFechaInicio(), programada.getFrecuencia(), ocurrencia);
        LocalDate fin = programada.getFechaFin();
        return fin != null && siguiente.isAfter(fin) ? null : siguiente;
    }

    private static CrearTransaccionRequest solicitud(
            TransaccionProgramada programada, LocalDate ocurrencia) {
        return new CrearTransaccionRequest(
                programada.getCuenta().getId(),
                ocurrencia,
                programada.getMonto(),
                programada.getCategoria() == null ? null : programada.getCategoria().getId(),
                programada.getBeneficiario(),
                programada.getMemo(),
                false,
                List.of());
    }

    /** En una transacción propia (la de la plantilla ya se revirtió); si falla, solo se anota. */
    private void registrarError(Long id, RuntimeException fallo) {
        String motivo = fallo instanceof NegocioException
                ? fallo.getMessage()
                : MENSAJE_ERROR_INESPERADO;
        try {
            TransactionTemplate nueva = new TransactionTemplate(transactionManager);
            nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            nueva.executeWithoutResult(estado -> programadaRepository.findById(id)
                    .ifPresent(programada -> {
                        programada.registrarError(motivo);
                        programadaRepository.saveAndFlush(programada);
                    }));
        } catch (RuntimeException falloAlGuardar) {
            log.error("No se pudo guardar el error de la transacción programada {} ({})",
                    id, motivo, falloAlGuardar);
        }
    }
}
