package com.presupuesto.asignacion.service;

import com.presupuesto.asignacion.dto.request.AsignarRequest;
import com.presupuesto.asignacion.dto.request.MoverDineroRequest;
import com.presupuesto.asignacion.dto.response.AsignacionActualizadaResponse;
import com.presupuesto.asignacion.dto.response.MesPresupuestoResponse;
import com.presupuesto.asignacion.entity.AsignacionMensual;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class AsignacionService {

    static final String MENSAJE_CATEGORIA_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_MISMA_CATEGORIA = "El origen y el destino deben ser distintos";
    static final String MENSAJE_SIN_DISPONIBLE =
            "El origen no tiene disponible suficiente en ese mes";
    static final String MENSAJE_CONFLICTO =
            "La asignación cambió al mismo tiempo, vuelve a intentarlo";

    private final AsignacionMensualRepository asignacionRepository;
    private final CategoriaRepository categoriaRepository;
    private final CalculadoraMes calculadora;
    private final MesPresupuestoService mesService;
    private final PresupuestoService presupuestoService;

    /** Fija (no suma) el asignado de la categoría en el mes; crea la fila si no existe. */
    @Transactional
    public AsignacionActualizadaResponse asignar(
            Long presupuestoId,
            Long usuarioId,
            String mesTexto,
            Long categoriaId,
            AsignarRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        Categoria categoria = buscarCategoria(categoriaId, presupuestoId);
        AsignacionMensual asignacion = asignacionRepository
                .findByCategoriaIdAndMes(categoria.getId(), mes.atDay(1))
                .orElseGet(() -> nueva(categoria, mes, 0L));
        asignacion.fijarAsignado(request.asignado());
        guardar(List.of(asignacion));
        ResultadoMes resultado = calculadora.calcular(presupuestoId, mes);
        return AsignacionActualizadaResponse.desde(
                MesPresupuestoService.fila(categoria, resultado), resultado.listoParaAsignar());
    }

    /**
     * Resta {@code monto} al asignado del origen y lo suma al del destino. Todas las validaciones
     * (400, 404, 422) ocurren antes de cambiar nada y todo va en una sola transacción.
     */
    @Transactional
    public MesPresupuestoResponse moverDinero(
            Long presupuestoId, Long usuarioId, String mesTexto, MoverDineroRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        YearMonth mes = MesParametro.interpretar(mesTexto);
        if (request.origenId().equals(request.destinoId())) {
            throw new DatosInvalidosException(MENSAJE_MISMA_CATEGORIA);
        }
        Categoria origen = buscarCategoria(request.origenId(), presupuestoId);
        Categoria destino = buscarCategoria(request.destinoId(), presupuestoId);
        long monto = request.monto();
        long disponible = calculadora.calcular(presupuestoId, mes).fila(origen.getId())
                .disponible();
        if (disponible < monto) {
            throw new ReglaNegocioException(MENSAJE_SIN_DISPONIBLE);
        }
        AsignacionMensual deOrigen = buscarOCrear(origen, mes);
        AsignacionMensual deDestino = buscarOCrear(destino, mes);
        deOrigen.sumarAsignado(-monto);
        deDestino.sumarAsignado(monto);
        guardar(List.of(deOrigen, deDestino));
        return mesService.construir(presupuestoId, mes, true);
    }

    private Categoria buscarCategoria(Long categoriaId, Long presupuestoId) {
        return categoriaRepository.findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_CATEGORIA_NO_ENCONTRADA));
    }

    private AsignacionMensual buscarOCrear(Categoria categoria, YearMonth mes) {
        LocalDate primerDia = mes.atDay(1);
        return asignacionRepository.findByCategoriaIdAndMes(categoria.getId(), primerDia)
                .orElseGet(() -> nueva(categoria, mes, 0L));
    }

    private static AsignacionMensual nueva(Categoria categoria, YearMonth mes, long asignado) {
        return AsignacionMensual.builder()
                .categoria(categoria)
                .mes(mes.atDay(1))
                .asignado(asignado)
                .build();
    }

    /** Una petición concurrente que cree la misma fila choca con la restricción única. */
    private void guardar(List<AsignacionMensual> asignaciones) {
        try {
            asignacionRepository.saveAllAndFlush(asignaciones);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(MENSAJE_CONFLICTO);
        }
    }
}
