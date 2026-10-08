package com.presupuesto.transaccionprogramada.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.service.TransaccionService;
import com.presupuesto.transaccionprogramada.dto.request.ActualizarProgramadaRequest;
import com.presupuesto.transaccionprogramada.dto.request.CrearProgramadaRequest;
import com.presupuesto.transaccionprogramada.dto.response.GeneracionResponse;
import com.presupuesto.transaccionprogramada.dto.response.TransaccionProgramadaResponse;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import com.presupuesto.transaccionprogramada.repository.TransaccionProgramadaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class TransaccionProgramadaService {

    static final String MENSAJE_NO_ENCONTRADA = "Transacción programada no encontrada";
    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "Cuenta no encontrada";
    static final String MENSAJE_CATEGORIA_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_FIN_ANTERIOR_AL_INICIO =
            "La fecha de fin no puede ser anterior a la de inicio";

    private final TransaccionProgramadaRepository programadaRepository;
    private final TransaccionRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final CategoriaRepository categoriaRepository;
    private final PresupuestoService presupuestoService;
    private final TransaccionService transaccionService;
    private final GeneradorProgramadas generador;
    private final Clock clock;

    /** Nace activa y sin generar nada, aunque la fecha de inicio sea pasada. */
    @Transactional
    public TransaccionProgramadaResponse crear(
            Long presupuestoId, Long usuarioId, CrearProgramadaRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        exigirFinValido(request.fechaInicio(), request.fechaFin());
        Cuenta cuenta = cuentaRepository
                .findByIdAndPresupuestoId(request.cuentaId(), presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        Categoria categoria = categoria(request.categoriaId(), presupuestoId);
        transaccionService.exigirRegistrable(cuenta, categoria);
        TransaccionProgramada programada = TransaccionProgramada.builder()
                .presupuesto(presupuesto)
                .cuenta(cuenta)
                .fechaInicio(request.fechaInicio())
                .frecuencia(request.frecuencia())
                .fechaFin(request.fechaFin())
                .monto(request.monto())
                .categoria(categoria)
                .beneficiario(request.beneficiario())
                .memo(request.memo())
                .proximaFecha(request.fechaInicio())
                .build();
        return TransaccionProgramadaResponse.desde(programadaRepository.saveAndFlush(programada));
    }

    @Transactional(readOnly = true)
    public List<TransaccionProgramadaResponse> listar(
            Long presupuestoId, Long usuarioId, boolean soloActivas) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return programadaRepository.listar(presupuestoId, soloActivas).stream()
                .map(TransaccionProgramadaResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransaccionProgramadaResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        return TransaccionProgramadaResponse.desde(buscar(presupuestoId, usuarioId, id));
    }

    /**
     * Cambia solo las ocurrencias futuras. Si está activa y cambia la frecuencia (o estaba
     * finalizada y se amplía la fecha de fin) recalcula {@code proximaFecha}; una pausada no la
     * recalcula, lo hace al reanudar.
     */
    @Transactional
    public TransaccionProgramadaResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarProgramadaRequest request) {
        TransaccionProgramada programada = buscar(presupuestoId, usuarioId, id);
        Categoria categoria = categoria(request.categoriaId(), presupuestoId);
        exigirFinValido(programada.getFechaInicio(), request.fechaFin());
        transaccionService.exigirCategoriaRegistrable(categoria);
        boolean cambiaFrecuencia = programada.getFrecuencia() != request.frecuencia();
        programada.editar(
                request.monto(),
                categoria,
                request.beneficiario(),
                request.memo(),
                request.frecuencia(),
                request.fechaFin());
        if (programada.isActiva()) {
            if (cambiaFrecuencia || programada.getProximaFecha() == null) {
                programada.reprogramar(primeraPendiente(programada));
            } else if (superaElFin(programada, programada.getProximaFecha())) {
                programada.reprogramar(null);
            }
        }
        return TransaccionProgramadaResponse.desde(programadaRepository.saveAndFlush(programada));
    }

    /** Las transacciones ya generadas se conservan sin vínculo con la plantilla. */
    @Transactional
    public void borrar(Long presupuestoId, Long usuarioId, Long id) {
        TransaccionProgramada programada = buscar(presupuestoId, usuarioId, id);
        transaccionRepository.desvincularProgramada(programada.getId());
        programadaRepository.deleteById(programada.getId());
    }

    /** Idempotente. */
    @Transactional
    public TransaccionProgramadaResponse pausar(Long presupuestoId, Long usuarioId, Long id) {
        TransaccionProgramada programada = buscar(presupuestoId, usuarioId, id);
        programada.pausar();
        return TransaccionProgramadaResponse.desde(programadaRepository.saveAndFlush(programada));
    }

    /**
     * No genera las ocurrencias del periodo pausado: la siguiente es la primera igual o posterior
     * a hoy (y posterior a la última generada). Ni siquiera la de hoy se crea aquí: la crea la
     * siguiente ejecución del generador. Reanudar una plantilla activa no cambia nada.
     */
    @Transactional
    public TransaccionProgramadaResponse reanudar(Long presupuestoId, Long usuarioId, Long id) {
        TransaccionProgramada programada = buscar(presupuestoId, usuarioId, id);
        if (!programada.isActiva()) {
            LocalDate hoy = LocalDate.now(clock);
            LocalDate ultima = programada.getUltimaOcurrenciaGenerada();
            LocalDate desde = ultima != null && !ultima.isBefore(hoy) ? ultima.plusDays(1) : hoy;
            LocalDate proxima = CalendarioProgramado.primeraDesde(
                    programada.getFechaInicio(), programada.getFrecuencia(), desde);
            programada.activar(superaElFin(programada, proxima) ? null : proxima);
        }
        return TransaccionProgramadaResponse.desde(programadaRepository.saveAndFlush(programada));
    }

    /** Genera las vencidas solo de este presupuesto, hasta hoy. */
    public GeneracionResponse generar(Long presupuestoId, Long usuarioId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return GeneracionResponse.desde(
                generador.generarVencidas(presupuestoId, LocalDate.now(clock)));
    }

    /** Primera posterior a la última generada (o el inicio); {@code null} si pasa el fin. */
    private static LocalDate primeraPendiente(TransaccionProgramada programada) {
        LocalDate ultima = programada.getUltimaOcurrenciaGenerada();
        LocalDate candidata = ultima == null
                ? programada.getFechaInicio()
                : CalendarioProgramado.primeraDespuesDe(
                        programada.getFechaInicio(), programada.getFrecuencia(), ultima);
        return superaElFin(programada, candidata) ? null : candidata;
    }

    private static boolean superaElFin(TransaccionProgramada programada, LocalDate fecha) {
        return programada.getFechaFin() != null && fecha.isAfter(programada.getFechaFin());
    }

    private static void exigirFinValido(LocalDate inicio, LocalDate fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw new DatosInvalidosException(MENSAJE_FIN_ANTERIOR_AL_INICIO);
        }
    }

    /** {@code null} si no se indica; 404 si no es del presupuesto (oculta sí sirve). */
    private Categoria categoria(Long categoriaId, Long presupuestoId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_CATEGORIA_NO_ENCONTRADA));
    }

    private TransaccionProgramada buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return programadaRepository.findByIdAndPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADA));
    }
}
