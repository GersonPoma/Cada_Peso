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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return TransaccionResponse.desde(
                guardar(presupuestoId, presupuesto, request, null, null));
    }

    /**
     * Crea la transacción de una ocurrencia de una transacción programada. No valida el usuario:
     * quien llama ya resolvió el presupuesto. Pasa por las mismas reglas que {@link #crear}; la
     * fecha de ocurrencia y el id de la plantilla quedan guardados (y son únicos juntos).
     */
    @Transactional
    public Transaccion crearProgramada(
            Presupuesto presupuesto,
            CrearTransaccionRequest request,
            Long programadaId,
            LocalDate fechaOcurrencia) {
        return guardar(
                presupuesto.getId(), presupuesto, request, programadaId, fechaOcurrencia);
    }

    /**
     * Crea varias transacciones de una misma cuenta en la transacción actual (todas o ninguna),
     * con las mismas reglas que {@link #crear}: cuenta abierta (422) y categoría válida. No valida
     * el usuario: quien llama ya resolvió presupuesto y cuenta. Los beneficiarios se crean si no
     * existen y se reutilizan dentro del lote; hace un solo {@code flush} al final.
     */
    @Transactional
    public List<Transaccion> crearLote(
            Presupuesto presupuesto, Cuenta cuenta, List<CrearTransaccionRequest> requests) {
        referencias.exigirAbierta(cuenta);
        Map<String, Beneficiario> beneficiarios = new HashMap<>();
        List<Transaccion> nuevas = new ArrayList<>(requests.size());
        for (CrearTransaccionRequest request : requests) {
            nuevas.add(construir(
                    presupuesto.getId(), presupuesto, cuenta, request, null, null, beneficiarios));
        }
        List<Transaccion> guardadas = transaccionRepository.saveAll(nuevas);
        transaccionRepository.flush();
        return guardadas;
    }

    /**
     * Cuántas transacciones de la cuenta hay por clave, entre {@code desde} y {@code hasta}. El
     * beneficiario se normaliza aquí (no en la base) para comparar igual que al crearlo.
     */
    @Transactional(readOnly = true)
    public Map<ClaveMovimiento, Integer> contarExistentesPorClave(
            Long cuentaId, LocalDate desde, LocalDate hasta) {
        Map<ClaveMovimiento, Integer> conteo = new HashMap<>();
        for (TransaccionRepository.ConteoPorClave fila :
                transaccionRepository.contarPorClave(cuentaId, desde, hasta)) {
            conteo.merge(
                    ClaveMovimiento.de(fila.getFecha(), fila.getMonto(), fila.getBeneficiario()),
                    Math.toIntExact(fila.getCantidad()),
                    Integer::sum);
        }
        return conteo;
    }

    /** Cuenta abierta y categoría que no sea de pago de tarjeta (422); sin categoría, la omite. */
    public void exigirRegistrable(Cuenta cuenta, Categoria categoria) {
        referencias.exigirAbierta(cuenta);
        referencias.exigirNoEsDePago(categoria);
    }

    /** 422 si la categoría es de pago de tarjeta; sin categoría ({@code null}) la omite. */
    public void exigirCategoriaRegistrable(Categoria categoria) {
        referencias.exigirNoEsDePago(categoria);
    }

    private Transaccion guardar(
            Long presupuestoId,
            Presupuesto presupuesto,
            CrearTransaccionRequest request,
            Long programadaId,
            LocalDate fechaOcurrencia) {
        Cuenta cuenta = referencias.cuenta(request.cuentaId(), presupuestoId);
        return transaccionRepository.saveAndFlush(construir(
                presupuestoId, presupuesto, cuenta, request, programadaId, fechaOcurrencia, null));
    }

    /**
     * Arma la transacción sin guardarla. {@code beneficiarios} es una caché opcional (por nombre
     * normalizado) que evita consultar el mismo beneficiario varias veces en un lote.
     */
    private Transaccion construir(
            Long presupuestoId,
            Presupuesto presupuesto,
            Cuenta cuenta,
            CrearTransaccionRequest request,
            Long programadaId,
            LocalDate fechaOcurrencia,
            Map<String, Beneficiario> beneficiarios) {
        TransaccionReferencias.Division division = referencias.dividir(
                presupuestoId, request.categoriaId(), request.monto(), request.subtransacciones());
        referencias.exigirAbierta(cuenta);
        Beneficiario beneficiario =
                vincular(presupuesto, request.beneficiario(), division, beneficiarios);
        Transaccion transaccion = Transaccion.builder()
                .cuenta(cuenta)
                .fecha(request.fecha())
                .monto(request.monto())
                .categoria(division.categoria())
                .beneficiario(beneficiario == null ? null : beneficiario.getNombre())
                .beneficiarioVinculado(beneficiario)
                .memo(request.memo())
                .aprobada(request.aprobada())
                .programadaId(programadaId)
                .fechaOcurrencia(fechaOcurrencia)
                .build();
        transaccion.reemplazarSubtransacciones(division.partes());
        return transaccion;
    }

    /** Beneficiario de los ajustes de conciliación. */
    public static final String BENEFICIARIO_AJUSTE = "Ajuste de conciliación";

    /**
     * Crea el ajuste de una conciliación: transacción de la cuenta por {@code monto}, con la
     * categoría indicada (o ninguna), estado {@code CONCILIADA} y aprobada. No valida el usuario
     * ni si la cuenta está cerrada: quien llama ya lo hizo. El beneficiario se crea si no
     * existe, sin recordar categoría.
     */
    @Transactional
    public Transaccion crearAjuste(
            Presupuesto presupuesto, Cuenta cuenta, LocalDate fecha, long monto, Long categoriaId) {
        Categoria categoria =
                referencias.categoriaParaRegistrar(categoriaId, presupuesto.getId());
        Beneficiario beneficiario =
                beneficiarioService.obtenerOCrear(presupuesto, BENEFICIARIO_AJUSTE);
        return transaccionRepository.saveAndFlush(Transaccion.builder()
                .cuenta(cuenta)
                .fecha(fecha)
                .monto(monto)
                .categoria(categoria)
                .beneficiario(beneficiario.getNombre())
                .beneficiarioVinculado(beneficiario)
                .estado(EstadoTransaccion.CONCILIADA)
                .aprobada(true)
                .build());
    }

    /** Pasa a {@code RECONCILIADA} las {@code CONCILIADA} de la cuenta hasta la fecha. */
    @Transactional
    public int reconciliarHasta(Long cuentaId, LocalDate hasta) {
        return transaccionRepository.reconciliarConciliadasHasta(
                cuentaId, hasta, Instant.now(clock));
    }

    /** Las {@code NO_CONCILIADA} de la cuenta, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Transaccion> listarNoConciliadas(Long cuentaId, int limite) {
        return transaccionRepository.noConciliadasDeCuenta(cuentaId, PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public long contarNoConciliadas(Long cuentaId) {
        return transaccionRepository.contarNoConciliadasDeCuenta(cuentaId);
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
        return vincular(presupuesto, texto, division, null);
    }

    private Beneficiario vincular(
            Presupuesto presupuesto,
            String texto,
            TransaccionReferencias.Division division,
            Map<String, Beneficiario> cache) {
        if (texto == null) {
            return null;
        }
        Beneficiario beneficiario;
        if (cache == null) {
            beneficiario = beneficiarioService.obtenerOCrear(presupuesto, texto);
        } else {
            beneficiario = cache.computeIfAbsent(
                    Beneficiario.normalizar(texto),
                    clave -> beneficiarioService.obtenerOCrear(presupuesto, texto));
        }
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
