package com.presupuesto.meta.service;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.asignacion.service.MesParametro;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.response.MetaMesResponse;
import com.presupuesto.meta.dto.response.MetasMesResponse;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.MetaPospuesta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.meta.service.CalculoMeta.Resultado;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado de las metas de un mes y posponer o reanudar una meta en un mes. Toda operación valida
 * primero el presupuesto y después interpreta el mes.
 */
@Service
@RequiredArgsConstructor
public class MetaMesService {

    private final MetaRepository metaRepository;
    private final MetaPospuestaRepository pospuestaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MesPresupuestoService mesService;
    private final PresupuestoService presupuestoService;

    @Transactional(readOnly = true)
    public MetasMesResponse obtener(
            Long presupuestoId, Long usuarioId, String mesTexto, boolean incluirOcultas) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        ResultadoMes resultado = mesService.calcular(presupuestoId, mes);
        Set<Long> pospuestas = new HashSet<>(
                pospuestaRepository.findMetaIdsPospuestas(presupuestoId, mes.atDay(1)));
        List<MetaMesResponse> metas = metaRepository
                .findDelPresupuestoEnOrdenDelArbol(presupuestoId).stream()
                .filter(meta -> incluirOcultas || !meta.getCategoria().isOculta())
                .map(meta -> elemento(meta, mes, resultado, pospuestas.contains(meta.getId())))
                .toList();
        return MetasMesResponse.desde(mes, metas);
    }

    /** Pospone la meta solo en ese mes; si ya estaba pospuesta no cambia nada. */
    @Transactional
    public MetaMesResponse posponer(
            Long presupuestoId, Long usuarioId, String mesTexto, Long categoriaId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        Meta meta = buscarMeta(categoriaId, presupuestoId);
        if (pospuestaRepository.findByMetaIdAndMes(meta.getId(), mes.atDay(1)).isEmpty()) {
            try {
                pospuestaRepository.saveAndFlush(
                        MetaPospuesta.builder().meta(meta).mes(mes.atDay(1)).build());
            } catch (DataIntegrityViolationException e) {
                throw new ConflictoException(MetaService.MENSAJE_CONFLICTO);
            }
        }
        return elemento(meta, mes, mesService.calcular(presupuestoId, mes), true);
    }

    /** Quita la pospuesta de ese mes; si no estaba pospuesta no cambia nada. */
    @Transactional
    public MetaMesResponse reanudar(
            Long presupuestoId, Long usuarioId, String mesTexto, Long categoriaId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        Meta meta = buscarMeta(categoriaId, presupuestoId);
        pospuestaRepository.findByMetaIdAndMes(meta.getId(), mes.atDay(1))
                .ifPresent(pospuestaRepository::delete);
        pospuestaRepository.flush();
        return elemento(meta, mes, mesService.calcular(presupuestoId, mes), false);
    }

    private Meta buscarMeta(Long categoriaId, Long presupuestoId) {
        Categoria categoria = categoriaRepository
                .findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        MetaService.MENSAJE_CATEGORIA_NO_ENCONTRADA));
        return metaRepository.findByCategoriaId(categoria.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(MetaService.MENSAJE_SIN_META));
    }

    private static MetaMesResponse elemento(
            Meta meta, YearMonth mes, ResultadoMes resultado, boolean pospuesta) {
        FilaMes fila = resultado.fila(meta.getCategoria().getId());
        Resultado calculo = CalculoMeta.calcular(meta, mes, fila, pospuesta);
        return MetaMesResponse.desde(
                meta,
                calculo.necesidad(),
                fila.asignado(),
                fila.disponible(),
                calculo.faltante(),
                calculo.estado());
    }
}
