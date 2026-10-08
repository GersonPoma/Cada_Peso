package com.presupuesto.asignacion.service;

import com.presupuesto.asignacion.repository.ActividadMensualRepository;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.SumaTarjetaPorMes;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository.AsignadoPorMes;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.CategoriaRepository.PagoDeTarjeta;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma las entradas del cálculo con consultas agregadas (asignaciones, actividad e ingresos del
 * presupuesto hasta el mes pedido) y delega las reglas en {@link CalculoMensual}. La actividad de
 * la categoría de pago de cada tarjeta sale de {@link CalculoPagoTarjeta} y se suma a la de las
 * demás categorías, que no cambia.
 */
@Component
@RequiredArgsConstructor
class CalculadoraMes {

    private final AsignacionMensualRepository asignacionRepository;
    private final ActividadMensualRepository actividadRepository;
    private final CategoriaRepository categoriaRepository;

    /** Asignado y actividad por categoría y mes, hasta el mes pedido: lo que consume el cálculo */
    private record Entradas(
            Map<Long, Map<YearMonth, Long>> asignado, Map<Long, Map<YearMonth, Long>> actividad) {}

    ResultadoMes calcular(Long presupuestoId, YearMonth mes) {
        Entradas entradas = cargar(presupuestoId, mes);
        LocalDate finDeMes = mes.atEndOfMonth();
        long ingresos = actividadRepository.ingresosSinCategoria(presupuestoId, finDeMes)
                + actividadRepository.saldosInicialesPositivos(presupuestoId);
        return CalculoMensual.calcular(mes, entradas.asignado(), entradas.actividad(), ingresos);
    }

    /**
     * Las cifras de cada categoría en cada mes de {@code desde} a {@code hasta}, con una sola
     * carga de datos: los mapas se arman una vez hasta {@code hasta} y {@link CalculoMensual}
     * (que solo mira los meses hasta el que calcula) se aplica a cada mes en memoria. Cada mes
     * trae exactamente las mismas filas que {@link #calcular}: como los mapas incluyen meses
     * posteriores, se descartan las categorías cuyo primer dato es posterior al mes. No incluye
     * {@code listoParaAsignar}, que depende de los ingresos y aquí no se consultan.
     */
    Map<YearMonth, Map<Long, FilaMes>> calcularFilas(
            Long presupuestoId, YearMonth desde, YearMonth hasta) {
        Entradas entradas = cargar(presupuestoId, hasta);
        Map<Long, YearMonth> primerDato = primerDatoPorCategoria(entradas);
        Map<YearMonth, Map<Long, FilaMes>> filas = new LinkedHashMap<>();
        for (YearMonth siguiente = desde; !siguiente.isAfter(hasta);
                siguiente = siguiente.plusMonths(1)) {
            YearMonth mes = siguiente;
            Map<Long, FilaMes> delMes = new HashMap<>(CalculoMensual.calcular(
                    mes, entradas.asignado(), entradas.actividad(), 0L).filas());
            delMes.keySet().removeIf(categoriaId -> primerDato.get(categoriaId).isAfter(mes));
            filas.put(mes, delMes);
        }
        return filas;
    }

    /** El primer mes con asignado o actividad de cada categoría. */
    private static Map<Long, YearMonth> primerDatoPorCategoria(Entradas entradas) {
        Map<Long, YearMonth> primero = new HashMap<>();
        for (Map<Long, Map<YearMonth, Long>> datos :
                List.of(entradas.asignado(), entradas.actividad())) {
            datos.forEach((categoriaId, porMes) -> porMes.keySet().forEach(
                    mes -> primero.merge(categoriaId, mes,
                            (actual, nuevo) -> nuevo.isBefore(actual) ? nuevo : actual)));
        }
        return primero;
    }

    /** Asignaciones, actividad y reserva de pagos de tarjeta hasta el fin de {@code ultimo}. */
    private Entradas cargar(Long presupuestoId, YearMonth ultimo) {
        LocalDate finDeMes = ultimo.atEndOfMonth();
        Map<Long, Map<YearMonth, Long>> asignado = new HashMap<>();
        for (AsignadoPorMes fila : asignacionRepository.asignadosHasta(
                presupuestoId, ultimo.atDay(1))) {
            acumular(asignado, fila.getCategoriaId(), YearMonth.from(fila.getMes()),
                    fila.getAsignado());
        }
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        agregar(actividad, actividadRepository.actividadSimple(presupuestoId, finDeMes));
        agregar(actividad, actividadRepository.actividadDividida(presupuestoId, finDeMes));
        agregarPagosDeTarjetas(actividad, presupuestoId, finDeMes);
        return new Entradas(asignado, actividad);
    }

    /**
     * Suma a la actividad la reserva de las categorías de pago: gastos con categoría de cada
     * tarjeta y pagos recibidos. Sin tarjetas con categoría de pago no hace ninguna consulta.
     */
    private void agregarPagosDeTarjetas(
            Map<Long, Map<YearMonth, Long>> actividad, Long presupuestoId, LocalDate finDeMes) {
        Map<Long, Long> categoriaDePago = new HashMap<>();
        for (PagoDeTarjeta pago : categoriaRepository.pagosDeTarjetas(presupuestoId)) {
            categoriaDePago.put(pago.getCuentaId(), pago.getCategoriaId());
        }
        if (categoriaDePago.isEmpty()) {
            return;
        }
        Map<Long, Map<YearMonth, Long>> sumas = new HashMap<>();
        agregarTarjeta(sumas, actividadRepository.gastosTarjeta(presupuestoId, finDeMes));
        agregarTarjeta(sumas, actividadRepository.gastosTarjetaDivididos(presupuestoId, finDeMes));
        agregarTarjeta(sumas, actividadRepository.pagosATarjeta(presupuestoId, finDeMes));
        CalculoPagoTarjeta.actividadPorCategoria(sumas, categoriaDePago)
                .forEach((categoriaId, porMes) -> porMes.forEach(
                        (mes, valor) -> acumular(actividad, categoriaId, mes, valor)));
    }

    private static void agregarTarjeta(
            Map<Long, Map<YearMonth, Long>> destino, List<SumaTarjetaPorMes> filas) {
        for (SumaTarjetaPorMes fila : filas) {
            acumular(destino, fila.getCuentaId(), YearMonth.of(fila.getAnio(), fila.getMes()),
                    fila.getTotal());
        }
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
