package com.presupuesto.asignacion.service;

import com.presupuesto.asignacion.repository.ActividadMensualRepository;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository.AsignadoPorMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma las entradas del cálculo con consultas agregadas (asignaciones, actividad e ingresos del
 * presupuesto hasta el mes pedido) y delega las reglas en {@link CalculoMensual}.
 */
@Component
@RequiredArgsConstructor
class CalculadoraMes {

    private final AsignacionMensualRepository asignacionRepository;
    private final ActividadMensualRepository actividadRepository;

    ResultadoMes calcular(Long presupuestoId, YearMonth mes) {
        LocalDate finDeMes = mes.atEndOfMonth();
        Map<Long, Map<YearMonth, Long>> asignado = new HashMap<>();
        for (AsignadoPorMes fila : asignacionRepository.asignadosHasta(
                presupuestoId, mes.atDay(1))) {
            acumular(asignado, fila.getCategoriaId(), YearMonth.from(fila.getMes()),
                    fila.getAsignado());
        }
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        agregar(actividad, actividadRepository.actividadSimple(presupuestoId, finDeMes));
        agregar(actividad, actividadRepository.actividadDividida(presupuestoId, finDeMes));
        long ingresos = actividadRepository.ingresosSinCategoria(presupuestoId, finDeMes)
                + actividadRepository.saldosInicialesPositivos(presupuestoId);
        return CalculoMensual.calcular(mes, asignado, actividad, ingresos);
    }

    private static void agregar(
            Map<Long, Map<YearMonth, Long>> destino, List<ActividadPorMes> filas) {
        for (ActividadPorMes fila : filas) {
            acumular(destino, fila.getCategoriaId(), YearMonth.of(fila.getAnio(), fila.getMes()),
                    fila.getTotal());
        }
    }

    private static void acumular(
            Map<Long, Map<YearMonth, Long>> destino, Long categoriaId, YearMonth mes, long valor) {
        destino.computeIfAbsent(categoriaId, clave -> new HashMap<>()).merge(mes, valor, Long::sum);
    }
}
