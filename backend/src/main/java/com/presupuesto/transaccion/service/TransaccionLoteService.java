package com.presupuesto.transaccion.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.LoteRequest;
import com.presupuesto.transaccion.dto.request.OperacionLote;
import com.presupuesto.transaccion.dto.response.LoteResponse;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones sobre varias transacciones a la vez. Todas las validaciones ocurren antes de
 * cambiar nada y la operación es una sola transacción: o se aplica a todas o a ninguna.
 */
@Service
@RequiredArgsConstructor
public class TransaccionLoteService {

    static final String MENSAJE_CATEGORIA_OBLIGATORIA =
            "Categorizar en lote exige una categoría";
    static final String MENSAJE_CON_DIVISION =
            "No se puede categorizar una transacción dividida";

    private final TransaccionRepository transaccionRepository;
    private final TransaccionReferencias referencias;
    private final PresupuestoService presupuestoService;

    /**
     * Orden: 404 si algún id no es del presupuesto, 400 si falta la categoría, 404 si la
     * categoría es ajena y 422 por patas de transferencia (CATEGORIZAR y BORRAR), por reconciliadas (CATEGORIZAR y BORRAR; APROBAR las acepta,
     * igual que aprobar una sola) o por divididas (CATEGORIZAR).
     */
    @Transactional
    public LoteResponse ejecutar(Long presupuestoId, Long usuarioId, LoteRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Set<Long> ids = new LinkedHashSet<>(request.ids());
        List<Transaccion> transacciones =
                transaccionRepository.findByIdInAndCuentaPresupuestoId(ids, presupuestoId);
        if (transacciones.size() != ids.size()) {
            throw new RecursoNoEncontradoException(TransaccionService.MENSAJE_NO_ENCONTRADA);
        }
        OperacionLote operacion = request.operacion();
        Categoria categoria = null;
        if (operacion == OperacionLote.CATEGORIZAR) {
            if (request.categoriaId() == null) {
                throw new DatosInvalidosException(MENSAJE_CATEGORIA_OBLIGATORIA);
            }
            categoria = referencias.categoria(request.categoriaId(), presupuestoId);
        }
        validar(operacion, transacciones);
        switch (operacion) {
            case CATEGORIZAR -> {
                for (Transaccion transaccion : transacciones) {
                    transaccion.categorizar(categoria);
                    if (transaccion.getBeneficiarioVinculado() != null) {
                        transaccion.getBeneficiarioVinculado().recordarCategoria(categoria);
                    }
                }
                transaccionRepository.saveAllAndFlush(transacciones);
            }
            case APROBAR -> {
                for (Transaccion transaccion : transacciones) {
                    transaccion.aprobar();
                }
                transaccionRepository.saveAllAndFlush(transacciones);
            }
            case BORRAR -> {
                transaccionRepository.deleteAll(transacciones);
                transaccionRepository.flush();
            }
        }
        return new LoteResponse(ids.size());
    }

    private static void validar(OperacionLote operacion, List<Transaccion> transacciones) {
        for (Transaccion transaccion : transacciones) {
            if (operacion != OperacionLote.APROBAR && transaccion.esTransferencia()) {
                throw new ReglaNegocioException(TransaccionReferencias.MENSAJE_ES_TRANSFERENCIA);
            }
            if (operacion != OperacionLote.APROBAR && transaccion.estaReconciliada()) {
                throw new ReglaNegocioException(TransaccionService.MENSAJE_RECONCILIADA);
            }
            if (operacion == OperacionLote.CATEGORIZAR && transaccion.tieneSubtransacciones()) {
                throw new ReglaNegocioException(MENSAJE_CON_DIVISION);
            }
        }
    }
}
