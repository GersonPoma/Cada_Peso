package com.presupuesto.reporte.service;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaPospuestaRepository.PospuestaEnMes;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.meta.service.CalculoMeta;
import com.presupuesto.meta.service.CalculoMeta.Resultado;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.CumplimientoMetasResponse;
import com.presupuesto.reporte.dto.response.MesMetaResponse;
import com.presupuesto.reporte.dto.response.MetaCumplimientoResponse;
import com.presupuesto.reporte.service.CalculoCumplimiento.Mes;
import com.presupuesto.reporte.service.CalculoCumplimiento.Total;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cumplimiento de las metas en un rango de meses. No calcula nada nuevo de las metas: el
 * asignado, la actividad y el disponible salen de {@link MesPresupuestoService#calcularFilas}
 * (la misma ecuación del presupuesto mensual) y la necesidad, el faltante y el estado de
 * {@link CalculoMeta}, igual que el estado de metas de un mes. Orden de errores: presupuesto 404
 * y parámetros 400. La meta vigente se aplica a todos los meses (no hay historial de metas).
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ReporteProperties.class)
public class MetasReporteService {

    private final PresupuestoService presupuestoService;
    private final MesPresupuestoService mesService;
    private final MetaRepository metaRepository;
    private final MetaPospuestaRepository pospuestaRepository;
    private final ReporteProperties propiedades;

    /** Una meta pospuesta en un mes. */
    private record Pospuesta(Long metaId, YearMonth mes) {}

    /**
     * 7 consultas (10 con alguna tarjeta): presupuesto, metas, pospuestas del rango y las del
     * cálculo del rango (asignaciones, 2 de actividad, pagos de tarjetas y, si hay, 3 de sumas).
     */
    @Transactional(readOnly = true)
    public CumplimientoMetasResponse cumplimiento(
            Long presupuestoId, Long usuarioId, String desde, String hasta) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        RangoMeses rango = RangoMeses.interpretar(desde, hasta, propiedades.maxMeses(), true);
        List<Meta> metas = metaRepository.findDelPresupuestoEnOrdenDelArbol(presupuestoId);
        Set<Pospuesta> pospuestas = new HashSet<>();
        for (PospuestaEnMes fila : pospuestaRepository.findPospuestasEnRango(
                presupuestoId, rango.desde().atDay(1), rango.hasta().atDay(1))) {
            pospuestas.add(new Pospuesta(fila.getMetaId(), YearMonth.from(fila.getMes())));
        }
        Map<YearMonth, Map<Long, FilaMes>> filas =
                mesService.calcularFilas(presupuestoId, rango.desde(), rango.hasta());
        List<MetaCumplimientoResponse> respuesta = new ArrayList<>();
        for (Meta meta : metas) {
            respuesta.add(cumplimiento(meta, rango, filas, pospuestas));
        }
        return CumplimientoMetasResponse.desde(rango.desde(), rango.hasta(), respuesta);
    }

    private static MetaCumplimientoResponse cumplimiento(
            Meta meta,
            RangoMeses rango,
            Map<YearMonth, Map<Long, FilaMes>> filas,
            Set<Pospuesta> pospuestas) {
        Long categoriaId = meta.getCategoria().getId();
        boolean categoriaDePago = meta.getCategoria().getGrupo().esPagosTarjeta();
        List<MesMetaResponse> meses = new ArrayList<>();
        List<Mes> cifras = new ArrayList<>();
        for (YearMonth mes : rango.meses()) {
            FilaMes fila = filas.get(mes).getOrDefault(categoriaId, FilaMes.CERO);
            Resultado calculo = CalculoMeta.calcular(
                    meta, mes, fila, pospuestas.contains(new Pospuesta(meta.getId(), mes)));
            long gastado = CalculoCumplimiento.gastado(fila.actividad(), categoriaDePago);
            cifras.add(new Mes(calculo.necesidad(), fila.asignado(), gastado));
            meses.add(MesMetaResponse.desde(
                    mes,
                    calculo.necesidad(),
                    fila.asignado(),
                    gastado,
                    fila.disponible(),
                    calculo.faltante(),
                    calculo.estado(),
                    CalculoCumplimiento.porcentaje(fila.asignado(), calculo.necesidad())));
        }
        Total total = CalculoCumplimiento.totales(cifras);
        return MetaCumplimientoResponse.desde(
                meta, total.necesidad(), total.asignado(), total.gastado(), total.porcentaje(),
                meses);
    }
}
