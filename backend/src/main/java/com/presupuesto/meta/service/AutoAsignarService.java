package com.presupuesto.meta.service;

import com.presupuesto.asignacion.service.AsignacionService;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.asignacion.service.MesParametro;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.request.AutoAsignarRequest;
import com.presupuesto.meta.dto.request.EstrategiaAutoAsignar;
import com.presupuesto.meta.dto.response.AutoAsignarResponse;
import com.presupuesto.meta.dto.response.CambioAsignacionResponse;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fija el asignado del mes de varias categorías según una estrategia, o simula el resultado. Todo
 * en una sola transacción y un solo {@code fijarAsignados}. Valida primero el presupuesto, luego
 * interpreta el mes y por último resuelve las categorías (una ajena responde 404 sin aplicar
 * nada).
 */
@Service
@RequiredArgsConstructor
public class AutoAsignarService {

    private static final int MESES_PROMEDIO = 3;

    private final MetaRepository metaRepository;
    private final MetaPospuestaRepository pospuestaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MesPresupuestoService mesService;
    private final AsignacionService asignacionService;
    private final PresupuestoService presupuestoService;

    @Transactional
    public AutoAsignarResponse autoAsignar(
            Long presupuestoId, Long usuarioId, String mesTexto, AutoAsignarRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        List<Categoria> categorias = categoriasObjetivo(presupuestoId, request.categoriaIds());
        ResultadoMes actual = mesService.calcular(presupuestoId, mes);
        Map<Long, Long> nuevos = calcularNuevos(
                request.estrategia(), presupuestoId, mes, categorias, actual);

        List<CambioAsignacionResponse> cambios = new ArrayList<>();
        Map<Long, Long> aFijar = new LinkedHashMap<>();
        for (Categoria categoria : categorias) {
            Long nuevo = nuevos.get(categoria.getId());
            long antes = actual.fila(categoria.getId()).asignado();
            if (nuevo != null && nuevo != antes) {
                cambios.add(CambioAsignacionResponse.desde(categoria, antes, nuevo));
                aFijar.put(categoria.getId(), nuevo);
            }
        }
        if (!request.simular() && !aFijar.isEmpty()) {
            asignacionService.fijarAsignados(presupuestoId, mes, aFijar);
        }
        return AutoAsignarResponse.desde(!request.simular(), actual.listoParaAsignar(), cambios);
    }

    /**
     * Sin ids, las categorías visibles salvo las de pago de tarjeta (no se pisa lo que la persona
     * financió a mano); con ids, solo esas (ocultas y de pago incluidas) o 404.
     */
    private List<Categoria> categoriasObjetivo(Long presupuestoId, List<Long> categoriaIds) {
        if (categoriaIds == null) {
            return categoriaRepository
                    .findByGrupoPresupuestoIdAndOcultaFalseOrderByGrupoOrdenAscOrdenAsc(
                            presupuestoId)
                    .stream()
                    .filter(categoria -> !categoria.esPagoTarjeta())
                    .toList();
        }
        Set<Long> ids = new LinkedHashSet<>(categoriaIds);
        List<Categoria> categorias = categoriaRepository
                .findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc(presupuestoId, ids);
        if (categorias.size() != ids.size()) {
            throw new RecursoNoEncontradoException(MetaService.MENSAJE_CATEGORIA_NO_ENCONTRADA);
        }
        return categorias;
    }

    /** El nuevo asignado de cada categoría que la estrategia toca (las demás no aparecen). */
    private Map<Long, Long> calcularNuevos(
            EstrategiaAutoAsignar estrategia,
            Long presupuestoId,
            YearMonth mes,
            List<Categoria> categorias,
            ResultadoMes actual) {
        return switch (estrategia) {
            case FALTANTE_META -> faltanteDeMeta(presupuestoId, mes, categorias, actual);
            case ASIGNADO_MES_PASADO -> historial(
                    presupuestoId, mes, 1, categorias, FilaMes::asignado, false);
            case GASTADO_MES_PASADO -> historial(
                    presupuestoId, mes, 1, categorias, AutoAsignarService::gastado, false);
            case PROMEDIO_ASIGNADO -> historial(
                    presupuestoId, mes, MESES_PROMEDIO, categorias, FilaMes::asignado, true);
            case PROMEDIO_GASTADO -> historial(
                    presupuestoId, mes, MESES_PROMEDIO, categorias, AutoAsignarService::gastado,
                    true);
        };
    }

    /** {@code asignado + faltante} en las categorías con meta; las demás no cambian. */
    private Map<Long, Long> faltanteDeMeta(
            Long presupuestoId, YearMonth mes, List<Categoria> categorias, ResultadoMes actual) {
        Map<Long, Meta> metas = new HashMap<>();
        metaRepository.findDelPresupuestoEnOrdenDelArbol(presupuestoId)
                .forEach(meta -> metas.put(meta.getCategoria().getId(), meta));
        Set<Long> pospuestas = new HashSet<>(
                pospuestaRepository.findMetaIdsPospuestas(presupuestoId, mes.atDay(1)));
        Map<Long, Long> nuevos = new HashMap<>();
        for (Categoria categoria : categorias) {
            Meta meta = metas.get(categoria.getId());
            if (meta != null) {
                FilaMes fila = actual.fila(categoria.getId());
                long faltante = CalculoMeta.calcular(
                        meta, mes, fila, pospuestas.contains(meta.getId())).faltante();
                nuevos.put(categoria.getId(), fila.asignado() + faltante);
            }
        }
        return nuevos;
    }

    /**
     * Lo asignado o gastado en los {@code meses} anteriores: el del mes anterior si es uno, o la
     * suma dividida entre {@code meses} (hacia abajo) si es un promedio. Un mes anterior a 2000-01
     * cuenta 0 y no se calcula.
     */
    private Map<Long, Long> historial(
            Long presupuestoId,
            YearMonth mes,
            int meses,
            List<Categoria> categorias,
            ToLongFunction<FilaMes> valor,
            boolean promedio) {
        List<ResultadoMes> anteriores = new ArrayList<>();
        for (int atras = 1; atras <= meses; atras++) {
            YearMonth anterior = mes.minusMonths(atras);
            if (anterior.getYear() >= MesParametro.ANIO_MINIMO) {
                anteriores.add(mesService.calcular(presupuestoId, anterior));
            }
        }
        Map<Long, Long> nuevos = new HashMap<>();
        for (Categoria categoria : categorias) {
            long suma = 0L;
            for (ResultadoMes anterior : anteriores) {
                suma += valor.applyAsLong(anterior.fila(categoria.getId()));
            }
            nuevos.put(categoria.getId(), promedio ? Math.floorDiv(suma, meses) : suma);
        }
        return nuevos;
    }

    /** Lo gastado: la actividad negativa en positivo; un ingreso (actividad positiva) vale 0. */
    private static long gastado(FilaMes fila) {
        return Math.max(0L, -fila.actividad());
    }
}
