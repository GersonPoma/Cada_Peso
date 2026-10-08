package com.presupuesto.cuenta.service;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.dto.request.ActualizarCuentaRequest;
import com.presupuesto.cuenta.dto.request.CrearCuentaRequest;
import com.presupuesto.cuenta.dto.response.CuentaResponse;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.evento.CuentaCerradaEvento;
import com.presupuesto.cuenta.evento.CuentaCreadaEvento;
import com.presupuesto.cuenta.evento.CuentaReabiertaEvento;
import com.presupuesto.cuenta.evento.CuentaRenombradaEvento;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class CuentaService {

    static final String MENSAJE_NO_ENCONTRADA = "Cuenta no encontrada";
    static final String MENSAJE_YA_EXISTE = "Ya existe una cuenta con ese nombre";
    static final String MENSAJE_SALDO_NEGATIVO =
            "Solo las tarjetas de crédito y los préstamos admiten saldo inicial negativo";
    static final String MENSAJE_TIPO_TARJETA =
            "El tipo de una tarjeta de crédito no se puede cambiar, ni una cuenta pasar a tarjeta";

    private final CuentaRepository cuentaRepository;
    private final PresupuestoService presupuestoService;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public CuentaResponse crear(Long presupuestoId, Long usuarioId, CrearCuentaRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        exigirSaldoValido(request.saldoInicial(), request.tipo());
        String normalizado = Cuenta.normalizar(request.nombre());
        if (cuentaRepository.existsByPresupuestoIdAndNombreNormalizado(
                presupuestoId, normalizado)) {
            throw yaExiste();
        }
        Cuenta cuenta = guardar(Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(request.nombre())
                .nombreNormalizado(normalizado)
                .tipo(request.tipo())
                .enPresupuesto(request.enPresupuesto())
                .saldoInicial(request.saldoInicial())
                .build());
        eventos.publishEvent(new CuentaCreadaEvento(cuenta));
        return CuentaResponse.desde(cuenta);
    }

    @Transactional(readOnly = true)
    public List<CuentaResponse> listar(
            Long presupuestoId, Long usuarioId, boolean incluirCerradas) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        List<Cuenta> cuentas = incluirCerradas
                ? cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(presupuestoId)
                : cuentaRepository.findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado(
                        presupuestoId);
        return cuentas.stream().map(CuentaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        return CuentaResponse.desde(buscar(presupuestoId, usuarioId, id));
    }

    /**
     * Solo nombre y tipo; {@code saldoInicial} y {@code enPresupuesto} nunca cambian. El tipo no
     * cambia desde ni hacia {@code TARJETA_CREDITO}: la tarjeta tiene una categoría de pago.
     */
    @Transactional
    public CuentaResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarCuentaRequest request) {
        Cuenta cuenta = buscar(presupuestoId, usuarioId, id);
        exigirTipoCompatible(cuenta.getTipo(), request.tipo());
        exigirSaldoValido(cuenta.getSaldoInicial(), request.tipo());
        String normalizado = Cuenta.normalizar(request.nombre());
        if (cuentaRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                presupuestoId, normalizado, id)) {
            throw yaExiste();
        }
        boolean renombrada = !cuenta.getNombre().equals(request.nombre());
        cuenta.renombrar(request.nombre());
        cuenta.cambiarTipo(request.tipo());
        Cuenta guardada = guardar(cuenta);
        if (renombrada) {
            eventos.publishEvent(new CuentaRenombradaEvento(guardada));
        }
        return CuentaResponse.desde(guardada);
    }

    @Transactional
    public CuentaResponse cerrar(Long presupuestoId, Long usuarioId, Long id) {
        Cuenta cuenta = buscar(presupuestoId, usuarioId, id);
        cuenta.cerrar();
        Cuenta guardada = cuentaRepository.saveAndFlush(cuenta);
        eventos.publishEvent(new CuentaCerradaEvento(guardada));
        return CuentaResponse.desde(guardada);
    }

    @Transactional
    public CuentaResponse reabrir(Long presupuestoId, Long usuarioId, Long id) {
        Cuenta cuenta = buscar(presupuestoId, usuarioId, id);
        cuenta.reabrir();
        Cuenta guardada = cuentaRepository.saveAndFlush(cuenta);
        eventos.publishEvent(new CuentaReabiertaEvento(guardada));
        return CuentaResponse.desde(guardada);
    }

    private Cuenta buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return cuentaRepository.findByIdAndPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADA));
    }

    private static void exigirTipoCompatible(TipoCuenta actual, TipoCuenta nuevo) {
        boolean cambia = actual != nuevo;
        boolean tocaTarjeta =
                actual == TipoCuenta.TARJETA_CREDITO || nuevo == TipoCuenta.TARJETA_CREDITO;
        if (cambia && tocaTarjeta) {
            throw new ReglaNegocioException(MENSAJE_TIPO_TARJETA);
        }
    }

    private static void exigirSaldoValido(long saldoInicial, TipoCuenta tipo) {
        if (saldoInicial < 0 && !tipo.admiteSaldoNegativo()) {
            throw new ReglaNegocioException(MENSAJE_SALDO_NEGATIVO);
        }
    }

    /** Red de seguridad ante dos peticiones simultáneas con el mismo nombre. */
    private Cuenta guardar(Cuenta cuenta) {
        try {
            return cuentaRepository.saveAndFlush(cuenta);
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
    }

    private static ConflictoException yaExiste() {
        return new ConflictoException(CodigoError.CUENTA_YA_EXISTE, MENSAJE_YA_EXISTE);
    }
}
