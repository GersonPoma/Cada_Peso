package com.presupuesto.transaccion.service;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.service.BeneficiarioService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.comun.paginacion.PaginaResponse;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.ActualizarTransaccionRequest;
import com.presupuesto.transaccion.dto.request.CambiarEstadoRequest;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.dto.request.FiltroTransacciones;
import com.presupuesto.transaccion.dto.request.MoverCuentaRequest;
import com.presupuesto.transaccion.dto.response.TransaccionResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.repository.TransaccionSpecifications;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class TransaccionService {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    static final String MENSAJE_NO_ENCONTRADA = "Transacción no encontrada";
    static final String MENSAJE_RECONCILIADA =
            "Una transacción reconciliada no se puede modificar";
    static final String MENSAJE_ESTADO_MANUAL =
            "El estado solo se cambia manualmente entre no conciliada y conciliada";
    static final String MENSAJE_PAGINA_INVALIDA = "La página debe ser 0 o mayor";
    static final String MENSAJE_TAMANO_INVALIDO = "El tamaño debe estar entre 1 y 100";
    static final String MENSAJE_RANGO_INVALIDO = "La fecha desde no puede ser posterior a hasta";

    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fecha")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final TransaccionRepository transaccionRepository;
    private final TransaccionReferencias referencias;
    private final PresupuestoService presupuestoService;
    private final BeneficiarioService beneficiarioService;
    private final Clock clock;

    @Transactional
    public TransaccionResponse crear(
            Long presupuestoId, Long usuarioId, CrearTransaccionRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Cuenta cuenta = referencias.cuenta(request.cuentaId(), presupuestoId);
        TransaccionReferencias.Division division = referencias.dividir(
                presupuestoId, request.categoriaId(), request.monto(), request.subtransacciones());
        referencias.exigirAbierta(cuenta);
        Beneficiario beneficiario = vincular(presupuesto, request.beneficiario(), division);
        Transaccion transaccion = Transaccion.builder()
                .cuenta(cuenta)
                .fecha(request.fecha())
                .monto(request.monto())
                .categoria(division.categoria())
                .beneficiario(beneficiario == null ? null : beneficiario.getNombre())
                .beneficiarioVinculado(beneficiario)
                .memo(request.memo())
                .aprobada(request.aprobada())
                .build();
        transaccion.reemplazarSubtransacciones(division.partes());
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(transaccion));
    }

    /** {@code page} desde 0; {@code size} entre 1 y 100; cualquier otro valor es un 400. */
    @Transactional(readOnly = true)
    public PaginaResponse<TransaccionResponse> listar(
            Long presupuestoId, Long usuarioId, FiltroTransacciones filtro, int page, int size) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        if (page < 0) {
            throw new DatosInvalidosException(MENSAJE_PAGINA_INVALIDA);
        }
        if (size < 1 || size > TAMANO_MAXIMO) {
            throw new DatosInvalidosException(MENSAJE_TAMANO_INVALIDO);
        }
        if (filtro.desde() != null && filtro.hasta() != null
                && filtro.desde().isAfter(filtro.hasta())) {
            throw new DatosInvalidosException(MENSAJE_RANGO_INVALIDO);
        }
        Pageable pagina = PageRequest.of(page, size, ORDEN);
        return PaginaResponse.desde(
                transaccionRepository.findAll(especificacion(presupuestoId, filtro), pagina),
                TransaccionResponse::desde);
    }

    @Transactional(readOnly = true)
    public TransaccionResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        return TransaccionResponse.desde(buscar(presupuestoId, usuarioId, id));
    }

    /** La cuenta, el estado y {@code aprobada} no cambian; las subtransacciones se reemplazan. */
    @Transactional
    public TransaccionResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarTransaccionRequest request) {
        Transaccion transaccion = buscar(presupuestoId, usuarioId, id);
        referencias.exigirNoEsTransferencia(transaccion);
        TransaccionReferencias.Division division = referencias.dividir(
                presupuestoId, request.categoriaId(), request.monto(), request.subtransacciones());
        exigirNoReconciliada(transaccion);
        referencias.exigirAbierta(transaccion.getCuenta());
        Beneficiario beneficiario = vincular(
                transaccion.getCuenta().getPresupuesto(), request.beneficiario(), division);
        transaccion.editar(
                request.fecha(),
                request.monto(),
                division.categoria(),
                beneficiario,
                request.memo());
        transaccion.reemplazarSubtransacciones(division.partes());
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(transaccion));
    }

    @Transactional
    public void borrar(Long presupuestoId, Long usuarioId, Long id) {
        Transaccion transaccion = buscar(presupuestoId, usuarioId, id);
        referencias.exigirNoEsTransferencia(transaccion);
        exigirNoReconciliada(transaccion);
        transaccionRepository.delete(transaccion);
    }

    /** Idempotente: aprobar una ya aprobada no cambia nada. */
    @Transactional
    public TransaccionResponse aprobar(Long presupuestoId, Long usuarioId, Long id) {
        Transaccion transaccion = buscar(presupuestoId, usuarioId, id);
        transaccion.aprobar();
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(transaccion));
    }

    /** Solo entre NO_CONCILIADA y CONCILIADA; RECONCILIADA la fijará la conciliación. */
    @Transactional
    public TransaccionResponse cambiarEstado(
            Long presupuestoId, Long usuarioId, Long id, CambiarEstadoRequest request) {
        Transaccion transaccion = buscar(presupuestoId, usuarioId, id);
        exigirNoReconciliada(transaccion);
        if (request.estado() == EstadoTransaccion.RECONCILIADA) {
            throw new ReglaNegocioException(MENSAJE_ESTADO_MANUAL);
        }
        transaccion.cambiarEstado(request.estado());
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(transaccion));
    }

    @Transactional
    public TransaccionResponse moverCuenta(
            Long presupuestoId, Long usuarioId, Long id, MoverCuentaRequest request) {
        Transaccion transaccion = buscar(presupuestoId, usuarioId, id);
        referencias.exigirNoEsTransferencia(transaccion);
        Cuenta destino = referencias.cuenta(request.cuentaId(), presupuestoId);
        exigirNoReconciliada(transaccion);
        referencias.exigirAbierta(destino);
        transaccion.moverA(destino);
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(transaccion));
    }

    /** Copia con la fecha de hoy, NO_CONCILIADA y aprobada, con sus subtransacciones. */
    @Transactional
    public TransaccionResponse duplicar(Long presupuestoId, Long usuarioId, Long id) {
        Transaccion original = buscar(presupuestoId, usuarioId, id);
        referencias.exigirNoEsTransferencia(original);
        referencias.exigirAbierta(original.getCuenta());
        Transaccion copia = Transaccion.builder()
                .cuenta(original.getCuenta())
                .fecha(LocalDate.now(clock))
                .monto(original.getMonto())
                .categoria(original.getCategoria())
                .beneficiario(original.getBeneficiario())
                .beneficiarioVinculado(original.getBeneficiarioVinculado())
                .memo(original.getMemo())
                .estado(EstadoTransaccion.NO_CONCILIADA)
                .aprobada(true)
                .build();
        List<SubTransaccion> partes = new ArrayList<>();
        for (SubTransaccion parte : original.getSubtransacciones()) {
            partes.add(SubTransaccion.builder()
                    .categoria(parte.getCategoria())
                    .monto(parte.getMonto())
                    .memo(parte.getMemo())
                    .build());
        }
        copia.reemplazarSubtransacciones(partes);
        return TransaccionResponse.desde(transaccionRepository.saveAndFlush(copia));
    }

    /**
     * Beneficiario de la transacción ({@code null} si no llegó texto), creado si no existía. Si
     * la transacción queda con categoría propia (no dividida) se recuerda como la última usada con
     * él. Se llama después de todas las validaciones para no dejar beneficiarios de una petición
     * rechazada.
     */
    private Beneficiario vincular(
            Presupuesto presupuesto,
            String texto,
            TransaccionReferencias.Division division) {
        if (texto == null) {
            return null;
        }
        Beneficiario beneficiario = beneficiarioService.obtenerOCrear(presupuesto, texto);
        if (division.categoria() != null) {
            beneficiario.recordarCategoria(division.categoria());
        }
        return beneficiario;
    }

    private Transaccion buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return transaccionRepository.findByIdAndCuentaPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADA));
    }

    private static void exigirNoReconciliada(Transaccion transaccion) {
        if (transaccion.estaReconciliada()) {
            throw new ReglaNegocioException(MENSAJE_RECONCILIADA);
        }
    }

    private Specification<Transaccion> especificacion(
            Long presupuestoId, FiltroTransacciones filtro) {
        Specification<Transaccion> resultado =
                TransaccionSpecifications.delPresupuesto(presupuestoId);
        if (filtro.cuentaId() != null) {
            Cuenta cuenta = referencias.cuenta(filtro.cuentaId(), presupuestoId);
            resultado = resultado.and(TransaccionSpecifications.deLaCuenta(cuenta.getId()));
        }
        if (filtro.categoriaId() != null) {
            Categoria categoria = referencias.categoria(filtro.categoriaId(), presupuestoId);
            resultado = resultado.and(TransaccionSpecifications.deLaCategoria(categoria.getId()));
        }
        if (filtro.desde() != null) {
            resultado = resultado.and(TransaccionSpecifications.desde(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            resultado = resultado.and(TransaccionSpecifications.hasta(filtro.hasta()));
        }
        if (filtro.estado() != null) {
            resultado = resultado.and(TransaccionSpecifications.conEstado(filtro.estado()));
        }
        if (filtro.soloSinAprobar()) {
            resultado = resultado.and(TransaccionSpecifications.sinAprobar());
        }
        if (filtro.q() != null) {
            resultado = resultado.and(TransaccionSpecifications.contiene(filtro.q()));
        }
        return resultado;
    }
}
