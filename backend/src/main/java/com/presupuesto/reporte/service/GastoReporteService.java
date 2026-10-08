package com.presupuesto.reporte.service;

import com.presupuesto.asignacion.repository.ActividadMensualRepository;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.SinCategoriaPorMes;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.TotalPorMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.CategoriaGastoResponse;
import com.presupuesto.reporte.dto.response.GastoPorCategoriaResponse;
import com.presupuesto.reporte.dto.response.GrupoGastoResponse;
import com.presupuesto.reporte.dto.response.IngresosGastosResponse;
import com.presupuesto.reporte.dto.response.MesIngresosGastosResponse;
import com.presupuesto.reporte.dto.response.SinCategoriaGastoResponse;
import com.presupuesto.reporte.service.CalculoGasto.Gasto;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongUnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gasto por categoría e ingresos contra gastos. Orden de errores: presupuesto 404 y parámetros
 * 400. El gasto sale de las mismas consultas de actividad que el presupuesto mensual; las
 * consultas son un número fijo, sin una por mes ni por categoría.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ReporteProperties.class)
public class GastoReporteService {

    private final PresupuestoService presupuestoService;
    private final ActividadMensualRepository actividadRepository;
    private final GrupoCategoriaRepository grupoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ReporteProperties propiedades;

    /** Gasto en el rango y gasto sin categoría e ingresos por mes. */
    private record Datos(
            Gasto gasto, Map<YearMonth, Long> sinCategoria, Map<YearMonth, Long> ingresos) {}

    /** 7 consultas: presupuesto, 2 de actividad, 2 sin categoría, grupos y categorías. */
    @Transactional(readOnly = true)
    public GastoPorCategoriaResponse gastoPorCategoria(
            Long presupuestoId, Long usuarioId, String desde, String hasta) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        RangoMeses rango = RangoMeses.interpretar(desde, hasta, propiedades.maxMeses(), false);
        Datos datos = cargar(presupuestoId, rango);
        long sinCategoria = CalculoGasto.total(datos.sinCategoria());
        long totalGeneral = CalculoGasto.total(datos.gasto().porMes()) + sinCategoria;
        LongUnaryOperator porcentajeDe = total -> Porcentaje.centesimas(total, totalGeneral);
        return GastoPorCategoriaResponse.desde(
                rango.desde(),
                rango.hasta(),
                grupos(presupuestoId, datos.gasto().porCategoria(), porcentajeDe),
                SinCategoriaGastoResponse.desde(sinCategoria, porcentajeDe));
    }

    /** 5 consultas: presupuesto, 2 de actividad y 2 sin categoría. */
    @Transactional(readOnly = true)
    public IngresosGastosResponse ingresosGastos(
            Long presupuestoId, Long usuarioId, String desde, String hasta) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        RangoMeses rango = RangoMeses.interpretar(desde, hasta, propiedades.maxMeses(), false);
        Datos datos = cargar(presupuestoId, rango);
        List<MesIngresosGastosResponse> meses = rango.meses().stream()
                .map(mes -> MesIngresosGastosResponse.desde(
                        mes,
                        datos.ingresos().getOrDefault(mes, 0L),
                        datos.gasto().porMes().get(mes) + datos.sinCategoria().get(mes)))
                .toList();
        return IngresosGastosResponse.desde(rango.desde(), rango.hasta(), meses);
    }

    private Datos cargar(Long presupuestoId, RangoMeses rango) {
        LocalDate inicio = rango.desde().atDay(1);
        LocalDate fin = rango.hasta().atEndOfMonth();
        Map<Long, Map<YearMonth, Long>> actividad = new HashMap<>();
        agregar(actividad, actividadRepository.actividadSimple(presupuestoId, fin));
        agregar(actividad, actividadRepository.actividadDividida(presupuestoId, fin));
        Map<YearMonth, Long> salidasSimples = new HashMap<>();
        Map<YearMonth, Long> ingresos = new HashMap<>();
        for (SinCategoriaPorMes fila :
                actividadRepository.sinCategoriaPorMes(presupuestoId, inicio, fin)) {
            YearMonth mes = YearMonth.of(fila.getAnio(), fila.getMes());
            salidasSimples.merge(mes, fila.getSalidas(), Long::sum);
            ingresos.merge(mes, fila.getIngresos(), Long::sum);
        }
        Map<YearMonth, Long> salidasDivididas = new HashMap<>();
        for (TotalPorMes fila :
                actividadRepository.salidasDivididasSinCategoria(presupuestoId, inicio, fin)) {
            salidasDivididas.merge(
                    YearMonth.of(fila.getAnio(), fila.getMes()), fila.getTotal(), Long::sum);
        }
        return new Datos(
                CalculoGasto.calcular(actividad, rango),
                CalculoGasto.sinCategoria(salidasSimples, salidasDivididas, rango),
                ingresos);
    }

    /** Grupos y categorías con movimientos en el rango, en el orden del árbol. */
    private List<GrupoGastoResponse> grupos(
            Long presupuestoId, Map<Long, Long> gastoPorCategoria, LongUnaryOperator porcentajeDe) {
        Map<Long, List<CategoriaGastoResponse>> porGrupo = new HashMap<>();
        for (Categoria categoria :
                categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuestoId)) {
            Long total = gastoPorCategoria.get(categoria.getId());
            if (total != null) {
                porGrupo.computeIfAbsent(categoria.getGrupo().getId(), clave -> new ArrayList<>())
                        .add(CategoriaGastoResponse.desde(categoria, total, porcentajeDe));
            }
        }
        List<GrupoGastoResponse> grupos = new ArrayList<>();
        for (GrupoCategoria grupo :
                grupoRepository.findByPresupuestoIdOrderByOrden(presupuestoId)) {
            List<CategoriaGastoResponse> categorias = porGrupo.get(grupo.getId());
            if (categorias != null) {
                grupos.add(GrupoGastoResponse.desde(grupo, categorias, porcentajeDe));
            }
        }
        return grupos;
    }

    private static void agregar(
            Map<Long, Map<YearMonth, Long>> destino, List<ActividadPorMes> filas) {
        for (ActividadPorMes fila : filas) {
            destino.computeIfAbsent(fila.getCategoriaId(), clave -> new LinkedHashMap<>())
                    .merge(YearMonth.of(fila.getAnio(), fila.getMes()), fila.getTotal(),
                            Long::sum);
        }
    }
}
