package com.presupuesto.transaccion.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.ActualizarTransferenciaRequest;
import com.presupuesto.transaccion.dto.request.CrearTransferenciaRequest;
import com.presupuesto.transaccion.dto.response.TransferenciaResponse;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferencias entre dos cuentas: dos transacciones enlazadas que se crean, editan y borran
 * siempre juntas, en una sola transacción de base de datos. Toda operación valida primero que el
 * presupuesto sea del usuario.
 */
@Service
@RequiredArgsConstructor
public class TransferenciaService {

    static final String MENSAJE_NO_ENCONTRADA = "Transferencia no encontrada";
    static final String MENSAJE_MISMA_CUENTA = "El origen y el destino deben ser distintos";
    static final String MENSAJE_CATEGORIA_NO_PERMITIDA =
            "Una transferencia entre cuentas del mismo tipo no lleva categoría";
    static final String MENSAJE_CATEGORIA_OBLIGATORIA =
            "Una transferencia que saca dinero del presupuesto exige una categoría";

    private final TransaccionRepository transaccionRepository;
    private final TransaccionReferencias referencias;
    private final PresupuestoService presupuestoService;

    /** Orden: 400, 404 (cuentas, categoría), 422 (cerradas, regla de categoría). */
    @Transactional
    public TransferenciaResponse crear(
            Long presupuestoId, Long usuarioId, CrearTransferenciaRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        if (request.cuentaOrigenId().equals(request.cuentaDestinoId())) {
            throw new DatosInvalidosException(MENSAJE_MISMA_CUENTA);
        }
        Cuenta origen = referencias.cuenta(request.cuentaOrigenId(), presupuestoId);
        Cuenta destino = referencias.cuenta(request.cuentaDestinoId(), presupuestoId);
        Categoria categoria = referencias.categoria(request.categoriaId(), presupuestoId);
        referencias.exigirAbierta(origen);
        referencias.exigirAbierta(destino);
        exigirReglaDeCategoria(origen, destino, categoria);
        Transaccion salida = pata(origen, request.fecha(), -request.monto(), request.memo());
        Transaccion entrada = pata(destino, request.fecha(), request.monto(), request.memo());
        asignarCategoria(salida, entrada, categoria);
        transaccionRepository.saveAllAndFlush(List.of(salida, entrada));
        salida.enlazarCon(entrada);
        entrada.enlazarCon(salida);
        transaccionRepository.saveAllAndFlush(List.of(salida, entrada));
        return TransferenciaResponse.desde(salida, entrada);
    }

    @Transactional(readOnly = true)
    public TransferenciaResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        Transaccion[] patas = buscar(presupuestoId, usuarioId, id);
        return TransferenciaResponse.desde(patas[0], patas[1]);
    }

    /** Las cuentas, el estado y {@code aprobada} de las patas no cambian. */
    @Transactional
    public TransferenciaResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarTransferenciaRequest request) {
        Transaccion[] patas = buscar(presupuestoId, usuarioId, id);
        Transaccion salida = patas[0];
        Transaccion entrada = patas[1];
        Categoria categoria = referencias.categoria(request.categoriaId(), presupuestoId);
        exigirNoReconciliadas(salida, entrada);
        referencias.exigirAbierta(salida.getCuenta());
        referencias.exigirAbierta(entrada.getCuenta());
        exigirReglaDeCategoria(salida.getCuenta(), entrada.getCuenta(), categoria);
        salida.editar(
                request.fecha(),
                -request.monto(),
                null,
                salida.getBeneficiarioVinculado(),
                request.memo());
        entrada.editar(
                request.fecha(),
                request.monto(),
                null,
                entrada.getBeneficiarioVinculado(),
                request.memo());
        asignarCategoria(salida, entrada, categoria);
        transaccionRepository.saveAllAndFlush(List.of(salida, entrada));
        return TransferenciaResponse.desde(salida, entrada);
    }

    /** Desenlaza antes de borrar: cada fila apunta a la otra y la clave foránea lo impediría. */
    @Transactional
    public void borrar(Long presupuestoId, Long usuarioId, Long id) {
        Transaccion[] patas = buscar(presupuestoId, usuarioId, id);
        exigirNoReconciliadas(patas[0], patas[1]);
        List<Transaccion> ambas = List.of(patas[0], patas[1]);
        patas[0].desenlazar();
        patas[1].desenlazar();
        transaccionRepository.saveAllAndFlush(ambas);
        transaccionRepository.deleteAll(ambas);
        transaccionRepository.flush();
    }

    /** {@code [salida, entrada]} de la transferencia a la que pertenece la transacción. */
    private Transaccion[] buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Transaccion pata = transaccionRepository.findByIdAndCuentaPresupuestoId(id, presupuestoId)
                .filter(Transaccion::esTransferencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADA));
        Transaccion par = pata.getTransaccionPar();
        return pata.getMonto() < 0
                ? new Transaccion[] {pata, par}
                : new Transaccion[] {par, pata};
    }

    private static Transaccion pata(Cuenta cuenta, LocalDate fecha, long monto, String memo) {
        return Transaccion.builder().cuenta(cuenta).fecha(fecha).monto(monto).memo(memo).build();
    }

    /** Misma clase de cuenta: sin categoría; sale del presupuesto: obligatoria; entra: opcional. */
    private static void exigirReglaDeCategoria(
            Cuenta origen, Cuenta destino, Categoria categoria) {
        boolean origenEnPresupuesto = origen.isEnPresupuesto();
        if (origenEnPresupuesto == destino.isEnPresupuesto()) {
            if (categoria != null) {
                throw new ReglaNegocioException(MENSAJE_CATEGORIA_NO_PERMITIDA);
            }
        } else if (origenEnPresupuesto && categoria == null) {
            throw new ReglaNegocioException(MENSAJE_CATEGORIA_OBLIGATORIA);
        }
    }

    /** La categoría va solo en la pata de la cuenta del presupuesto; la otra queda sin ella. */
    private static void asignarCategoria(
            Transaccion salida, Transaccion entrada, Categoria categoria) {
        if (categoria == null) {
            return;
        }
        if (salida.getCuenta().isEnPresupuesto()) {
            salida.categorizar(categoria);
        } else {
            entrada.categorizar(categoria);
        }
    }

    private static void exigirNoReconciliadas(Transaccion salida, Transaccion entrada) {
        if (salida.estaReconciliada() || entrada.estaReconciliada()) {
            throw new ReglaNegocioException(TransaccionService.MENSAJE_RECONCILIADA);
        }
    }
}
