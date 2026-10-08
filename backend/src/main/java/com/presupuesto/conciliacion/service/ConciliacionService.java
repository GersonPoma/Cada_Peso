package com.presupuesto.conciliacion.service;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.conciliacion.dto.request.CrearConciliacionRequest;
import com.presupuesto.conciliacion.dto.response.ConciliacionResponse;
import com.presupuesto.conciliacion.dto.response.EstadoConciliacionResponse;
import com.presupuesto.conciliacion.entity.Conciliacion;
import com.presupuesto.conciliacion.repository.ConciliacionRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.service.SaldoCuentaService;
import com.presupuesto.transaccion.service.TransaccionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orden de errores: presupuesto 404, cuenta 404, datos 400 y reglas de negocio 422. Crear toma
 * un único bloqueo de escritura sobre la cuenta; el estado y el historial no bloquean.
 */
@Service
@RequiredArgsConstructor
public class ConciliacionService {

    public static final int MAXIMO_NO_CONCILIADAS = 100;
    public static final int MAXIMO_HISTORIAL = 50;

    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "Cuenta no encontrada";
    static final String MENSAJE_SALDO_REQUERIDO = "El saldo del extracto es obligatorio";
    static final String MENSAJE_FECHA_REQUERIDA = "La fecha del extracto es obligatoria";
    static final String MENSAJE_FECHA_FUTURA = "La fecha del extracto no puede ser futura";
    static final String MENSAJE_CUENTA_CERRADA = "La cuenta está cerrada";
    static final String MENSAJE_DIFERENCIA_SIN_AJUSTE =
            "Hay una diferencia con el extracto; pide crear el ajuste para cerrar la conciliación";
    static final String MENSAJE_CATEGORIA_REQUERIDA =
            "El ajuste de esta cuenta necesita una categoría";
    static final String MENSAJE_CATEGORIA_NO_APLICA =
            "Una cuenta fuera del presupuesto no admite categoría en el ajuste";

    private final PresupuestoService presupuestoService;
    private final CuentaRepository cuentaRepository;
    private final SaldoCuentaService saldoCuentaService;
    private final TransaccionService transaccionService;
    private final ConciliacionRepository conciliacionRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EstadoConciliacionResponse estado(
            Long presupuestoId,
            Long usuarioId,
            Long cuentaId,
            Long saldoExtracto,
            LocalDate fecha) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Cuenta cuenta = cuentaRepository.findByIdAndPresupuestoId(cuentaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        validar(saldoExtracto, fecha);
        long alCorte = saldoCuentaService.saldoConciliadoAl(cuenta, fecha);
        List<Transaccion> pendientes =
                transaccionService.listarNoConciliadas(cuentaId, MAXIMO_NO_CONCILIADAS);
        return EstadoConciliacionResponse.desde(
                cuentaId,
                fecha,
                saldoExtracto,
                saldoCuentaService.saldoConciliadoAl(cuenta, SaldoCuentaService.SIN_LIMITE),
                alCorte,
                CalculoConciliacion.diferencia(saldoExtracto, alCorte),
                transaccionService.contarNoConciliadas(cuentaId),
                pendientes);
    }

    @Transactional
    public ConciliacionResponse crear(
            Long presupuestoId, Long usuarioId, Long cuentaId, CrearConciliacionRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Cuenta cuenta = cuentaRepository
                .findByIdAndPresupuestoIdParaActualizar(cuentaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        validar(request.saldoExtracto(), request.fecha());
        if (cuenta.isCerrada()) {
            throw new ReglaNegocioException(MENSAJE_CUENTA_CERRADA);
        }
        LocalDate fecha = request.fecha();
        long diferencia = CalculoConciliacion.diferencia(
                request.saldoExtracto(), saldoCuentaService.saldoConciliadoAl(cuenta, fecha));
        long ajuste = 0L;
        Long transaccionAjusteId = null;
        if (diferencia != 0) {
            if (!request.pideAjuste()) {
                throw new ReglaNegocioException(MENSAJE_DIFERENCIA_SIN_AJUSTE);
            }
            exigirCategoriaCoherente(cuenta, diferencia, request.categoriaId());
            transaccionAjusteId = transaccionService
                    .crearAjuste(presupuesto, cuenta, fecha, diferencia, request.categoriaId())
                    .getId();
            ajuste = diferencia;
        }
        int reconciliadas = transaccionService.reconciliarHasta(cuentaId, fecha);
        Conciliacion guardada = conciliacionRepository.saveAndFlush(Conciliacion.builder()
                .presupuesto(presupuesto)
                .cuenta(cuenta)
                .fecha(fecha)
                .saldoExtracto(request.saldoExtracto())
                .ajuste(ajuste)
                .transaccionAjusteId(transaccionAjusteId)
                .cantidadReconciliadas(reconciliadas)
                .build());
        return ConciliacionResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public List<ConciliacionResponse> historial(
            Long presupuestoId, Long usuarioId, Long cuentaId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        cuentaRepository.findByIdAndPresupuestoId(cuentaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        return conciliacionRepository
                .findByCuentaIdOrderByFechaDescIdDesc(cuentaId, PageRequest.of(0, MAXIMO_HISTORIAL))
                .stream()
                .map(ConciliacionResponse::desde)
                .toList();
    }

    private void validar(Long saldoExtracto, LocalDate fecha) {
        if (saldoExtracto == null) {
            throw new DatosInvalidosException(MENSAJE_SALDO_REQUERIDO);
        }
        if (fecha == null) {
            throw new DatosInvalidosException(MENSAJE_FECHA_REQUERIDA);
        }
        if (fecha.isAfter(LocalDate.now(clock))) {
            throw new DatosInvalidosException(MENSAJE_FECHA_FUTURA);
        }
    }

    private static void exigirCategoriaCoherente(Cuenta cuenta, long ajuste, Long categoriaId) {
        if (!cuenta.isEnPresupuesto() && categoriaId != null) {
            throw new ReglaNegocioException(MENSAJE_CATEGORIA_NO_APLICA);
        }
        if (categoriaId == null && CalculoConciliacion.exigeCategoria(
                cuenta.isEnPresupuesto(), cuenta.getTipo(), ajuste)) {
            throw new ReglaNegocioException(MENSAJE_CATEGORIA_REQUERIDA);
        }
    }
}
