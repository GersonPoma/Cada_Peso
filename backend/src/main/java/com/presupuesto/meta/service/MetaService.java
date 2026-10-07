package com.presupuesto.meta.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.request.GuardarMetaRequest;
import com.presupuesto.meta.dto.response.MetaResponse;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class MetaService {

    static final String MENSAJE_CATEGORIA_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_SIN_META = "La categoría no tiene meta";
    static final String MENSAJE_CONFLICTO =
            "La meta cambió al mismo tiempo, vuelve a intentarlo";

    private final MetaRepository metaRepository;
    private final MetaPospuestaRepository pospuestaRepository;
    private final CategoriaRepository categoriaRepository;
    private final PresupuestoService presupuestoService;

    /** Crea la meta de la categoría o reemplaza la que tiene (una sola por categoría). */
    @Transactional
    public MetaResponse guardar(
            Long presupuestoId, Long usuarioId, Long categoriaId, GuardarMetaRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Categoria categoria = buscarCategoria(categoriaId, presupuestoId);
        Meta meta = metaRepository.findByCategoriaId(categoria.getId())
                .orElseGet(() -> Meta.builder().categoria(categoria).build());
        meta.reemplazar(
                request.tipo(),
                request.monto(),
                request.frecuencia(),
                request.diaSemana(),
                request.intervaloDias(),
                request.fechaInicio(),
                request.fechaObjetivo());
        try {
            return MetaResponse.desde(metaRepository.saveAndFlush(meta));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(MENSAJE_CONFLICTO);
        }
    }

    @Transactional(readOnly = true)
    public MetaResponse obtener(Long presupuestoId, Long usuarioId, Long categoriaId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Categoria categoria = buscarCategoria(categoriaId, presupuestoId);
        return MetaResponse.desde(buscarMeta(categoria));
    }

    /** Borra la meta y antes sus pospuestas: una meta no tiene historial. */
    @Transactional
    public void borrar(Long presupuestoId, Long usuarioId, Long categoriaId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Categoria categoria = buscarCategoria(categoriaId, presupuestoId);
        Meta meta = buscarMeta(categoria);
        pospuestaRepository.deleteByMetaId(meta.getId());
        pospuestaRepository.flush();
        metaRepository.delete(meta);
    }

    /** Todas las metas del presupuesto (ocultas incluidas) en el orden del árbol. */
    @Transactional(readOnly = true)
    public List<MetaResponse> listar(Long presupuestoId, Long usuarioId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return metaRepository.findDelPresupuestoEnOrdenDelArbol(presupuestoId).stream()
                .map(MetaResponse::desde)
                .toList();
    }

    private Categoria buscarCategoria(Long categoriaId, Long presupuestoId) {
        return categoriaRepository.findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_CATEGORIA_NO_ENCONTRADA));
    }

    private Meta buscarMeta(Categoria categoria) {
        return metaRepository.findByCategoriaId(categoria.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_SIN_META));
    }
}
