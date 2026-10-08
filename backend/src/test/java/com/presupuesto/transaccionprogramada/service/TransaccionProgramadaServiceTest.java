package com.presupuesto.transaccionprogramada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
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
import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import com.presupuesto.transaccionprogramada.repository.TransaccionProgramadaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransaccionProgramadaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final long CERRADA_ID = 32L;
    private static final long CATEGORIA_ID = 50L;
    private static final long PAGO_ID = 51L;
    private static final long ID = 40L;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    @Mock
    private TransaccionProgramadaRepository programadaRepository;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private TransaccionService transaccionService;

    @Mock
    private GeneradorProgramadas generador;

    private RelojDePrueba reloj;
    private TransaccionProgramadaService service;
    private Presupuesto presupuesto;
    private Cuenta banco;
    private Cuenta cerrada;
    private Categoria comida;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        reloj.fijar(Instant.parse("2026-10-10T15:00:00Z"));
        service = new TransaccionProgramadaService(
                programadaRepository, transaccionRepository, cuentaRepository,
                categoriaRepository, presupuestoService, transaccionService, generador, reloj);
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        banco = Cuenta.builder().id(CUENTA_ID).build();
        cerrada = Cuenta.builder().id(CERRADA_ID).build();
        cerrada.cerrar();
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoId(CERRADA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cerrada));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(PAGO_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(
                        Categoria.builder().id(PAGO_ID).cuentaTarjeta(banco).build()));
        when(programadaRepository.saveAndFlush(any(TransaccionProgramada.class)))
                .then(returnsFirstArg());
        // El service real de transacciones aplica estas reglas; aquí se simulan con las mismas.
        org.mockito.Mockito.doAnswer(inv -> {
            Cuenta cuenta = inv.getArgument(0);
            Categoria categoria = inv.getArgument(1);
            if (cuenta.isCerrada() || (categoria != null && categoria.esPagoTarjeta())) {
                throw new ReglaNegocioException("no registrable");
            }
            return null;
        }).when(transaccionService).exigirRegistrable(any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            Categoria categoria = inv.getArgument(0);
            if (categoria != null && categoria.esPagoTarjeta()) {
                throw new ReglaNegocioException("categoria de pago");
            }
            return null;
        }).when(transaccionService).exigirCategoriaRegistrable(any());
    }

    private static LocalDate f(int anio, int mes, int dia) {
        return LocalDate.of(anio, mes, dia);
    }

    private TransaccionProgramada guardada(
            FrecuenciaProgramada frecuencia, LocalDate inicio, LocalDate proxima) {
        TransaccionProgramada programada = TransaccionProgramada.builder()
                .id(ID).presupuesto(presupuesto).cuenta(banco)
                .fechaInicio(inicio).frecuencia(frecuencia).monto(-1000L)
                .proximaFecha(proxima).build();
        when(programadaRepository.findByIdAndPresupuestoId(ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(programada));
        return programada;
    }

    private CrearProgramadaRequest crearRequest(
            long cuentaId, Long categoriaId, LocalDate inicio, LocalDate fin) {
        return new CrearProgramadaRequest(cuentaId, inicio, FrecuenciaProgramada.MENSUAL, fin,
                -5000L, categoriaId, "Casero", "renta");
    }

    private ActualizarProgramadaRequest actualizarRequest(
            FrecuenciaProgramada frecuencia, LocalDate fin, Long categoriaId) {
        return new ActualizarProgramadaRequest(
                -9000L, categoriaId, "Otro", "memo", frecuencia, fin);
    }

    // ---------- crear ----------

    @Test
    void crearGuardaActivaConLaProximaFechaIgualAlInicioYSinGenerarNada() {
        TransaccionProgramadaResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                crearRequest(CUENTA_ID, CATEGORIA_ID, f(2026, 7, 5), null));

        assertThat(respuesta.activa()).isTrue();
        assertThat(respuesta.proximaFecha()).isEqualTo(f(2026, 7, 5));
        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(respuesta.categoriaId()).isEqualTo(CATEGORIA_ID);
        assertThat(respuesta.beneficiario()).isEqualTo("Casero");
        assertThat(respuesta.ultimoError()).isNull();
        verify(programadaRepository).saveAndFlush(any());
        org.mockito.Mockito.verifyNoInteractions(generador);
    }

    @Test
    void crearConFinIgualAlInicioEsValidoYAnteriorDa400() {
        service.crear(PRESUPUESTO_ID, USUARIO_ID,
                crearRequest(CUENTA_ID, null, f(2026, 7, 5), f(2026, 7, 5)));

        assertThrows(DatosInvalidosException.class, () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                crearRequest(CUENTA_ID, null, f(2026, 7, 5), f(2026, 7, 4))));
    }

    @Test
    void crearConPresupuestoAjenoPropagaElErrorYNoGuarda() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, crearRequest(CUENTA_ID, null, f(2026, 7, 5), null)));
        verify(programadaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearConCuentaOCategoriaAjenaDa404AntesQueLos422() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(PRESUPUESTO_ID,
                USUARIO_ID, crearRequest(999L, null, f(2026, 7, 5), null)));
        // Cuenta cerrada y categoría ajena a la vez: gana el 404.
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(PRESUPUESTO_ID,
                USUARIO_ID, crearRequest(CERRADA_ID, 999L, f(2026, 7, 5), null)));
        verify(programadaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearEnCuentaCerradaOConCategoriaDePagoDa422() {
        assertThrows(ReglaNegocioException.class, () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                crearRequest(CERRADA_ID, null, f(2026, 7, 5), null)));
        assertThrows(ReglaNegocioException.class, () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                crearRequest(CUENTA_ID, PAGO_ID, f(2026, 7, 5), null)));
        verify(programadaRepository, never()).saveAndFlush(any());
    }

    // ---------- consultar ----------

    @Test
    void listarPidePorPresupuestoYFiltroDeActivas() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));
        when(programadaRepository.listar(PRESUPUESTO_ID, true)).thenReturn(List.of(programada));

        List<TransaccionProgramadaResponse> lista =
                service.listar(PRESUPUESTO_ID, USUARIO_ID, true);

        assertThat(lista).extracting(TransaccionProgramadaResponse::id).containsExactly(ID);
        verify(programadaRepository).listar(PRESUPUESTO_ID, true);
    }

    @Test
    void obtenerDeOtroPresupuestoOInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 999L));
    }

    // ---------- actualizar ----------

    @Test
    void actualizarCambiaLosCamposPeroNoLaCuentaNiElInicio() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));

        TransaccionProgramadaResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                ID, actualizarRequest(FrecuenciaProgramada.MENSUAL, null, CATEGORIA_ID));

        assertThat(respuesta.monto()).isEqualTo(-9000L);
        assertThat(respuesta.categoriaId()).isEqualTo(CATEGORIA_ID);
        assertThat(respuesta.beneficiario()).isEqualTo("Otro");
        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(programada.getFechaInicio()).isEqualTo(f(2026, 1, 15));
        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 11, 15));
    }

    @Test
    void cambiarLaFrecuenciaRecalculaDesdeLaPrimeraOcurrenciaPosteriorALaUltimaGenerada() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 10, 15));
        programada.registrarGeneracion(f(2026, 9, 15), f(2026, 10, 15));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.SEMANAL, null, null));

        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 9, 17));
    }

    @Test
    void cambiarLaFrecuenciaSinNadaGeneradoVuelveAlInicio() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 1, 15));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.DIARIA, null, null));

        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 1, 15));
    }

    @Test
    void cambiarLaFrecuenciaDeUnaPausadaNoRecalculaLaProximaFecha() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 6, 15));
        programada.pausar();

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.SEMANAL, null, null));

        assertThat(programada.isActiva()).isFalse();
        assertThat(programada.getFrecuencia()).isEqualTo(FrecuenciaProgramada.SEMANAL);
        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 6, 15));
    }

    @Test
    void ampliarLaFechaDeFinDeUnaFinalizadaLaReactiva() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), null);
        programada.registrarGeneracion(f(2026, 9, 15), null);

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.MENSUAL, f(2026, 12, 15), null));

        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 10, 15));
    }

    @Test
    void acortarLaFechaDeFinPorDebajoDeLaProximaLaFinaliza() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.MENSUAL, f(2026, 10, 31), null));

        assertThat(programada.getProximaFecha()).isNull();
    }

    @Test
    void actualizarValidaFinCategoriaYPlantilla() {
        guardada(FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));

        assertThrows(DatosInvalidosException.class, () -> service.actualizar(PRESUPUESTO_ID,
                USUARIO_ID, ID,
                actualizarRequest(FrecuenciaProgramada.MENSUAL, f(2026, 1, 14), null)));
        assertThrows(ReglaNegocioException.class, () -> service.actualizar(PRESUPUESTO_ID,
                USUARIO_ID, ID, actualizarRequest(FrecuenciaProgramada.MENSUAL, null, PAGO_ID)));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(PRESUPUESTO_ID,
                USUARIO_ID, ID, actualizarRequest(FrecuenciaProgramada.MENSUAL, null, 999L)));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(PRESUPUESTO_ID,
                USUARIO_ID, 999L, actualizarRequest(FrecuenciaProgramada.MENSUAL, null, null)));
    }

    // ---------- borrar, pausar, reanudar ----------

    @Test
    void borrarDesvinculaLasGeneradasYBorraLaPlantilla() {
        guardada(FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));

        service.borrar(PRESUPUESTO_ID, USUARIO_ID, ID);

        verify(transaccionRepository).desvincularProgramada(ID);
        verify(programadaRepository).deleteById(ID);
    }

    @Test
    void pausarEsIdempotente() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 15), f(2026, 11, 15));

        service.pausar(PRESUPUESTO_ID, USUARIO_ID, ID);
        TransaccionProgramadaResponse otra = service.pausar(PRESUPUESTO_ID, USUARIO_ID, ID);

        assertThat(programada.isActiva()).isFalse();
        assertThat(otra.activa()).isFalse();
        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 11, 15));
    }

    @Test
    void reanudarSaltaElPeriodoPausadoALaPrimeraOcurrenciaDesdeHoy() {
        // Mensual el día 5, pausada desde junio; hoy es 2026-10-10.
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 5), f(2026, 6, 5));
        programada.registrarGeneracion(f(2026, 5, 5), f(2026, 6, 5));
        programada.registrarError("fallo anterior");
        programada.pausar();

        TransaccionProgramadaResponse respuesta =
                service.reanudar(PRESUPUESTO_ID, USUARIO_ID, ID);

        assertThat(respuesta.activa()).isTrue();
        assertThat(respuesta.proximaFecha()).isEqualTo(f(2026, 11, 5));
        assertThat(respuesta.ultimoError()).isNull();
        org.mockito.Mockito.verifyNoInteractions(generador, transaccionService);
    }

    @Test
    void reanudarConOcurrenciaHoyLaDejaComoProximaSinGenerarla() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 10), f(2026, 6, 10));
        programada.pausar();

        TransaccionProgramadaResponse respuesta =
                service.reanudar(PRESUPUESTO_ID, USUARIO_ID, ID);

        assertThat(respuesta.proximaFecha()).isEqualTo(HOY);
        org.mockito.Mockito.verifyNoInteractions(generador, transaccionService);
    }

    @Test
    void reanudarConLaFechaDeFinSuperadaFinalizaLaPlantilla() {
        TransaccionProgramada programada = TransaccionProgramada.builder()
                .id(ID).presupuesto(presupuesto).cuenta(banco)
                .fechaInicio(f(2026, 1, 5)).frecuencia(FrecuenciaProgramada.MENSUAL)
                .fechaFin(f(2026, 8, 5)).monto(-1000L).proximaFecha(f(2026, 6, 5)).build();
        when(programadaRepository.findByIdAndPresupuestoId(ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(programada));
        programada.pausar();

        TransaccionProgramadaResponse respuesta =
                service.reanudar(PRESUPUESTO_ID, USUARIO_ID, ID);

        assertThat(respuesta.activa()).isTrue();
        assertThat(respuesta.proximaFecha()).isNull();
    }

    @Test
    void reanudarUnaActivaNoCambiaNada() {
        TransaccionProgramada programada = guardada(
                FrecuenciaProgramada.MENSUAL, f(2026, 1, 5), f(2026, 6, 5));

        TransaccionProgramadaResponse respuesta =
                service.reanudar(PRESUPUESTO_ID, USUARIO_ID, ID);

        assertThat(respuesta.activa()).isTrue();
        assertThat(programada.getProximaFecha()).isEqualTo(f(2026, 6, 5));
    }

    // ---------- generar ----------

    @Test
    void generarValidaElPresupuestoYDelegaEnElGeneradorConHoy() {
        when(generador.generarVencidas(PRESUPUESTO_ID, HOY))
                .thenReturn(new ResultadoGeneracion(2, 0));

        GeneracionResponse respuesta = service.generar(PRESUPUESTO_ID, USUARIO_ID);

        assertThat(respuesta.generadas()).isEqualTo(2);
        assertThat(respuesta.plantillasConError()).isZero();
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void generarConPresupuestoAjenoDa404SinTocarElGenerador() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.generar(PRESUPUESTO_ID, USUARIO_ID));
        org.mockito.Mockito.verifyNoInteractions(generador);
    }
}
