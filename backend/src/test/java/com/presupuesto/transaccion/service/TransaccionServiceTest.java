package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.service.BeneficiarioService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.comun.paginacion.PaginaResponse;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.ActualizarTransaccionRequest;
import com.presupuesto.transaccion.dto.request.CambiarEstadoRequest;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.dto.request.FiltroTransacciones;
import com.presupuesto.transaccion.dto.request.MoverCuentaRequest;
import com.presupuesto.transaccion.dto.request.SubTransaccionRequest;
import com.presupuesto.transaccion.dto.response.TransaccionResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransaccionServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final long OTRA_CUENTA_ID = 31L;
    private static final long CERRADA_ID = 32L;
    private static final long TRANSACCION_ID = 40L;
    private static final long CATEGORIA_ID = 50L;
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 1);

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private BeneficiarioService beneficiarioService;

    private final Map<String, Beneficiario> beneficiarios = new HashMap<>();
    private RelojDePrueba reloj;
    private TransaccionService service;
    private Cuenta banco;
    private Cuenta otraCuenta;
    private Cuenta cerrada;
    private Categoria comida;
    private Transaccion existente;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        service = new TransaccionService(
                transaccionRepository,
                new TransaccionReferencias(cuentaRepository, categoriaRepository),
                presupuestoService,
                beneficiarioService,
                reloj);
        when(beneficiarioService.obtenerOCrear(any(), anyString())).thenAnswer(inv -> {
            String nombre = inv.getArgument(1);
            return beneficiarios.computeIfAbsent(Beneficiario.normalizar(nombre), clave ->
                    Beneficiario.builder().nombre(nombre).nombreNormalizado(clave).build());
        });
        banco = Cuenta.builder().id(CUENTA_ID).build();
        otraCuenta = Cuenta.builder().id(OTRA_CUENTA_ID).build();
        cerrada = Cuenta.builder().id(CERRADA_ID).build();
        cerrada.cerrar();
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        existente = Transaccion.builder()
                .id(TRANSACCION_ID).cuenta(banco).fecha(FECHA).monto(-1000L).build();
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoId(OTRA_CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(otraCuenta));
        when(cuentaRepository.findByIdAndPresupuestoId(CERRADA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cerrada));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(TRANSACCION_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(existente));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class)))
                .then(returnsFirstArg());
    }

    // ---------- crear ----------

    @Test
    void crearConstruyeLaTransaccionConLosValoresPorDefecto() {
        TransaccionResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearTransaccionRequest(
                        CUENTA_ID, FECHA, -2500L, CATEGORIA_ID, "Tienda", "nota", null, null));

        ArgumentCaptor<Transaccion> captura = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).saveAndFlush(captura.capture());
        Transaccion guardada = captura.getValue();
        assertThat(guardada.getCuenta()).isSameAs(banco);
        assertThat(guardada.getCategoria()).isSameAs(comida);
        assertThat(guardada.getMonto()).isEqualTo(-2500L);
        assertThat(guardada.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(guardada.isAprobada()).isTrue();
        assertThat(respuesta.categoriaId()).isEqualTo(CATEGORIA_ID);
        assertThat(respuesta.beneficiario()).isEqualTo("Tienda");
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void crearUnaDivisionGuardaLasSubtransaccionesSinCategoriaPropia() {
        TransaccionResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearTransaccionRequest(CUENTA_ID, FECHA, -3000L, null, null, null, false,
                        List.of(new SubTransaccionRequest(CATEGORIA_ID, -1000L, null),
                                new SubTransaccionRequest(null, -2000L, "resto"))));

        assertThat(respuesta.categoriaId()).isNull();
        assertThat(respuesta.aprobada()).isFalse();
        assertThat(respuesta.subtransacciones()).hasSize(2);
        assertThat(respuesta.subtransacciones().get(0).categoriaId()).isEqualTo(CATEGORIA_ID);
    }

    @Test
    void crearConPresupuestoAjenoPropagaElErrorYNoGuardaNada() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID, crearSimple(CUENTA_ID)));
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearConCuentaOCategoriaAjenaDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID, crearSimple(999L)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID, new CrearTransaccionRequest(
                        CUENTA_ID, FECHA, -1L, 999L, null, null, null, null)));
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearEnCuentaCerradaDa422() {
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID, crearSimple(CERRADA_ID)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    // ---------- listar ----------

    @Test
    @SuppressWarnings("unchecked")
    void listarPaginaYMapeaConOrdenFechaEIdDescendente() {
        when(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(
                        List.of(existente), inv.getArgument(1, Pageable.class), 41));

        PaginaResponse<TransaccionResponse> pagina = service.listar(
                PRESUPUESTO_ID, USUARIO_ID, filtro(), 1, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(20);
        assertThat(pagina.totalElementos()).isEqualTo(41);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        ArgumentCaptor<Pageable> captura = ArgumentCaptor.forClass(Pageable.class);
        verify(transaccionRepository).findAll(any(Specification.class), captura.capture());
        assertThat(captura.getValue().getSort().toString()).isEqualTo("fecha: DESC,id: DESC");
    }

    @Test
    void listarConPaginaOTamanoFueraDeRangoDa400() {
        for (int[] invalido : new int[][] {{-1, 20}, {0, 0}, {0, 101}, {0, -5}}) {
            DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                    () -> service.listar(
                            PRESUPUESTO_ID, USUARIO_ID, filtro(), invalido[0], invalido[1]));
            assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarAceptaLosLimitesDeTamano() {
        Page<Transaccion> vacia = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0);
        when(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(vacia);

        service.listar(PRESUPUESTO_ID, USUARIO_ID, filtro(), 0, 1);
        service.listar(PRESUPUESTO_ID, USUARIO_ID, filtro(), 0, 100);
    }

    @Test
    void listarConDesdePosteriorAHastaDa400() {
        FiltroTransacciones invertido = new FiltroTransacciones(
                null, null, FECHA.plusDays(1), FECHA, null, false, null);

        assertThrows(DatosInvalidosException.class,
                () -> service.listar(PRESUPUESTO_ID, USUARIO_ID, invertido, 0, 20));
    }

    @Test
    void listarConCuentaOCategoriaDeFiltroAjenaDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.listar(PRESUPUESTO_ID, USUARIO_ID,
                        new FiltroTransacciones(999L, null, null, null, null, false, null),
                        0, 20));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.listar(PRESUPUESTO_ID, USUARIO_ID,
                        new FiltroTransacciones(null, 999L, null, null, null, false, null),
                        0, 20));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarConTodosLosFiltrosValidosConsultaElRepositorio() {
        when(transaccionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(PRESUPUESTO_ID, USUARIO_ID, new FiltroTransacciones(
                CUENTA_ID, CATEGORIA_ID, FECHA, FECHA, EstadoTransaccion.CONCILIADA, true, "x"),
                0, 20);

        verify(transaccionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ---------- obtener ----------

    @Test
    void obtenerDevuelveLaTransaccion() {
        assertThat(service.obtener(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID).id())
                .isEqualTo(TRANSACCION_ID);
    }

    @Test
    void cadaOperacionDa404ConPresupuestoAjenoYConTransaccionAjena() {
        List<Runnable> operaciones = List.of(
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 999L),
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 999L, edicion(-1L)),
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, 999L),
                () -> service.aprobar(PRESUPUESTO_ID, USUARIO_ID, 999L),
                () -> service.cambiarEstado(PRESUPUESTO_ID, USUARIO_ID, 999L,
                        new CambiarEstadoRequest(EstadoTransaccion.CONCILIADA)),
                () -> service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, 999L,
                        new MoverCuentaRequest(OTRA_CUENTA_ID)),
                () -> service.duplicar(PRESUPUESTO_ID, USUARIO_ID, 999L));

        for (Runnable operacion : operaciones) {
            assertThrows(RecursoNoEncontradoException.class, operacion::run);
        }

        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));
        List<Supplier<Object>> conPresupuestoAjeno = List.of(
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID),
                () -> service.aprobar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID),
                () -> service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID),
                () -> service.listar(PRESUPUESTO_ID, USUARIO_ID, filtro(), 0, 20));
        for (Supplier<Object> operacion : conPresupuestoAjeno) {
            assertThrows(RecursoNoEncontradoException.class, operacion::get);
        }
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID));
    }

    // ---------- actualizar ----------

    @Test
    void actualizarCambiaLosCamposYReemplazaLasSubtransacciones() {
        TransaccionResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                TRANSACCION_ID, new ActualizarTransaccionRequest(
                        FECHA.plusDays(3), -3000L, null, "Nuevo", null,
                        List.of(new SubTransaccionRequest(null, -1000L, null),
                                new SubTransaccionRequest(null, -2000L, null))));

        assertThat(respuesta.fecha()).isEqualTo(FECHA.plusDays(3));
        assertThat(respuesta.monto()).isEqualTo(-3000L);
        assertThat(respuesta.beneficiario()).isEqualTo("Nuevo");
        assertThat(respuesta.subtransacciones()).hasSize(2);
        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(respuesta.estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
    }

    @Test
    void actualizarUnaDivisionSinSubtransaccionesLaVuelveSimpleConCategoria() {
        existente.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-400L).build(),
                SubTransaccion.builder().monto(-600L).build()));

        TransaccionResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                TRANSACCION_ID, new ActualizarTransaccionRequest(
                        FECHA, -1000L, CATEGORIA_ID, null, null, null));

        assertThat(respuesta.subtransacciones()).isEmpty();
        assertThat(respuesta.categoriaId()).isEqualTo(CATEGORIA_ID);
    }

    @Test
    void actualizarUnaReconciliadaDa422YNoCambiaNada() {
        existente.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID, edicion(-5L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(existente.getMonto()).isEqualTo(-1000L);
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizarEnCuentaCerradaDa422() {
        existente.moverA(cerrada);

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID, edicion(-5L)));
        assertThat(existente.getMonto()).isEqualTo(-1000L);
    }

    @Test
    void actualizarConSumaIncorrectaDa422YConCategoriaAjenaDa404() {
        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                        new ActualizarTransaccionRequest(FECHA, -3000L, null, null, null,
                                List.of(new SubTransaccionRequest(null, -1000L, null),
                                        new SubTransaccionRequest(null, -1000L, null)))));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                        new ActualizarTransaccionRequest(FECHA, -1L, 999L, null, null, null)));
    }

    // ---------- borrar ----------

    @Test
    void borrarEliminaLaTransaccion() {
        service.borrar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID);

        verify(transaccionRepository).delete(existente);
    }

    @Test
    void borrarUnaReconciliadaDa422YNoBorra() {
        existente.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        assertThrows(ReglaNegocioException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID));
        verify(transaccionRepository, never()).delete(any(Transaccion.class));
    }

    // ---------- aprobar ----------

    @Test
    void aprobarEsIdempotente() {
        Transaccion sinAprobar = Transaccion.builder()
                .id(TRANSACCION_ID).cuenta(banco).fecha(FECHA).monto(-1L).aprobada(false).build();
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(TRANSACCION_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(sinAprobar));

        assertThat(service.aprobar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID).aprobada())
                .isTrue();
        assertThat(service.aprobar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID).aprobada())
                .isTrue();
    }

    // ---------- estado ----------

    @Test
    void cambiarEstadoPermiteConciliarYDesconciliar() {
        assertThat(cambiarEstado(EstadoTransaccion.CONCILIADA).estado())
                .isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(cambiarEstado(EstadoTransaccion.NO_CONCILIADA).estado())
                .isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(cambiarEstado(EstadoTransaccion.NO_CONCILIADA).estado())
                .isEqualTo(EstadoTransaccion.NO_CONCILIADA);
    }

    @Test
    void cambiarEstadoAReconciliadaDa422() {
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> cambiarEstado(EstadoTransaccion.RECONCILIADA));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(existente.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
    }

    @Test
    void cambiarElEstadoDeUnaReconciliadaDa422() {
        existente.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        assertThrows(ReglaNegocioException.class,
                () -> cambiarEstado(EstadoTransaccion.NO_CONCILIADA));
        assertThrows(ReglaNegocioException.class,
                () -> cambiarEstado(EstadoTransaccion.CONCILIADA));
        assertThat(existente.estaReconciliada()).isTrue();
    }

    // ---------- mover cuenta ----------

    @Test
    void moverCuentaCambiaLaCuenta() {
        TransaccionResponse respuesta = service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID,
                TRANSACCION_ID, new MoverCuentaRequest(OTRA_CUENTA_ID));

        assertThat(respuesta.cuentaId()).isEqualTo(OTRA_CUENTA_ID);
    }

    @Test
    void moverCuentaADestinoAjenoDa404YACerradaDa422() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                        new MoverCuentaRequest(999L)));
        assertThrows(ReglaNegocioException.class,
                () -> service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                        new MoverCuentaRequest(CERRADA_ID)));
        assertThat(existente.getCuenta()).isSameAs(banco);
    }

    @Test
    void moverDesdeUnaCuentaCerradaHaciaUnaAbiertaSePermite() {
        existente.moverA(cerrada);

        service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new MoverCuentaRequest(CUENTA_ID));

        assertThat(existente.getCuenta()).isSameAs(banco);
    }

    @Test
    void moverUnaReconciliadaDa422() {
        existente.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        assertThrows(ReglaNegocioException.class,
                () -> service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                        new MoverCuentaRequest(OTRA_CUENTA_ID)));
        assertThat(existente.getCuenta()).isSameAs(banco);
    }

    // ---------- duplicar ----------

    @Test
    void duplicarCopiaConLaFechaDelRelojEstadoYAprobacionPorDefecto() {
        Transaccion original = Transaccion.builder()
                .id(TRANSACCION_ID).cuenta(banco).fecha(FECHA).monto(-3000L)
                .beneficiario("Tienda").memo("nota")
                .estado(EstadoTransaccion.CONCILIADA).aprobada(false).build();
        original.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-1000L).categoria(comida).memo("a").build(),
                SubTransaccion.builder().monto(-2000L).build()));
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(TRANSACCION_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(original));

        TransaccionResponse copia = service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID);

        assertThat(copia.fecha()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(copia.estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(copia.aprobada()).isTrue();
        assertThat(copia.monto()).isEqualTo(-3000L);
        assertThat(copia.beneficiario()).isEqualTo("Tienda");
        assertThat(copia.memo()).isEqualTo("nota");
        assertThat(copia.subtransacciones()).hasSize(2);
        assertThat(copia.subtransacciones().get(0).categoriaId()).isEqualTo(CATEGORIA_ID);
        assertThat(original.getEstado()).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(original.getSubtransacciones()).hasSize(2);
    }

    @Test
    void duplicarUsaElRelojInyectadoYPermiteUnaReconciliada() {
        reloj.fijar(java.time.Instant.parse("2027-01-05T23:00:00Z"));
        existente.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        TransaccionResponse copia = service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID);

        assertThat(copia.fecha()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(copia.estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
    }

    @Test
    void duplicarEnCuentaCerradaDa422() {
        existente.moverA(cerrada);

        assertThrows(ReglaNegocioException.class,
                () -> service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID));
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    // ---------- beneficiario vinculado ----------

    @Test
    void crearConBeneficiarioNuevoLoCreaLoVinculaYUsaSuNombre() {
        TransaccionResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearTransaccionRequest(
                        CUENTA_ID, FECHA, -2500L, null, "Netflix", null, null, null));

        ArgumentCaptor<Transaccion> captura = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).saveAndFlush(captura.capture());
        Beneficiario netflix = beneficiarios.get("netflix");
        assertThat(captura.getValue().getBeneficiarioVinculado()).isSameAs(netflix);
        assertThat(respuesta.beneficiario()).isEqualTo("Netflix");
        verify(beneficiarioService).obtenerOCrear(any(), org.mockito.ArgumentMatchers.eq("Netflix"));
    }

    @Test
    void crearConUnBeneficiarioExistenteConOtrasMayusculasUsaSuNombre() {
        Beneficiario existente = Beneficiario.builder()
                .nombre("Netflix").nombreNormalizado("netflix").build();
        beneficiarios.put("netflix", existente);

        TransaccionResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearTransaccionRequest(
                        CUENTA_ID, FECHA, -2500L, null, "NETFLIX", null, null, null));

        assertThat(respuesta.beneficiario()).isEqualTo("Netflix");
        assertThat(beneficiarios).hasSize(1);
    }

    @Test
    void crearSinBeneficiarioNoLoBuscaNiLoVincula() {
        TransaccionResponse respuesta = service.crear(
                PRESUPUESTO_ID, USUARIO_ID, crearSimple(CUENTA_ID));

        assertThat(respuesta.beneficiario()).isNull();
        assertThat(respuesta.beneficiarioId()).isNull();
        verify(beneficiarioService, never()).obtenerOCrear(any(), anyString());
    }

    @Test
    void crearConCategoriaRecuerdaLaCategoriaDelBeneficiario() {
        service.crear(PRESUPUESTO_ID, USUARIO_ID, new CrearTransaccionRequest(
                CUENTA_ID, FECHA, -2500L, CATEGORIA_ID, "Netflix", null, null, null));

        assertThat(beneficiarios.get("netflix").getCategoriaPredeterminada()).isSameAs(comida);
    }

    @Test
    void crearSinCategoriaNoCambiaLaCategoriaDelBeneficiario() {
        Beneficiario existente = Beneficiario.builder()
                .nombre("Netflix").nombreNormalizado("netflix").categoriaPredeterminada(comida)
                .build();
        beneficiarios.put("netflix", existente);

        service.crear(PRESUPUESTO_ID, USUARIO_ID, new CrearTransaccionRequest(
                CUENTA_ID, FECHA, -2500L, null, "Netflix", null, null, null));

        assertThat(existente.getCategoriaPredeterminada()).isSameAs(comida);
    }

    @Test
    void crearUnaDivisionNoCambiaLaCategoriaDelBeneficiario() {
        service.crear(PRESUPUESTO_ID, USUARIO_ID, new CrearTransaccionRequest(
                CUENTA_ID, FECHA, -3000L, null, "Hipermaxi", null, null,
                List.of(new SubTransaccionRequest(CATEGORIA_ID, -1000L, null),
                        new SubTransaccionRequest(null, -2000L, null))));

        assertThat(beneficiarios.get("hipermaxi").getCategoriaPredeterminada()).isNull();
    }

    @Test
    void crearConCategoriaAjenaNoCreaElBeneficiario() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearTransaccionRequest(
                        CUENTA_ID, FECHA, -2500L, 999L, "Netflix", null, null, null)));

        verify(beneficiarioService, never()).obtenerOCrear(any(), anyString());
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizarVinculaAlBeneficiarioYRecuerdaLaCategoria() {
        TransaccionResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                TRANSACCION_ID, new ActualizarTransaccionRequest(
                        FECHA, -1000L, CATEGORIA_ID, "Spotify", null, null));

        Beneficiario spotify = beneficiarios.get("spotify");
        assertThat(existente.getBeneficiarioVinculado()).isSameAs(spotify);
        assertThat(respuesta.beneficiario()).isEqualTo("Spotify");
        assertThat(spotify.getCategoriaPredeterminada()).isSameAs(comida);
    }

    @Test
    void actualizarUnaDivisionNoCambiaLaCategoriaDelBeneficiario() {
        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new ActualizarTransaccionRequest(FECHA, -3000L, null, "Spotify", null,
                        List.of(new SubTransaccionRequest(CATEGORIA_ID, -1000L, null),
                                new SubTransaccionRequest(null, -2000L, null))));

        assertThat(beneficiarios.get("spotify").getCategoriaPredeterminada()).isNull();
    }

    @Test
    void actualizarSinBeneficiarioQuitaElVinculo() {
        existente.editar(FECHA, -1000L, null,
                Beneficiario.builder().nombre("Netflix").build(), null);

        TransaccionResponse respuesta = service.actualizar(
                PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID, edicion(-1000L));

        assertThat(respuesta.beneficiario()).isNull();
        assertThat(respuesta.beneficiarioId()).isNull();
        assertThat(existente.getBeneficiarioVinculado()).isNull();
    }

    @Test
    void actualizarUnaTransaccionAnteriorSinVinculoLaVinculaAlEditarla() {
        Transaccion anterior = Transaccion.builder()
                .id(TRANSACCION_ID).cuenta(banco).fecha(FECHA).monto(-1000L)
                .beneficiario("Tienda").build();
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(TRANSACCION_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(anterior));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new ActualizarTransaccionRequest(FECHA, -1000L, null, "Tienda", null, null));

        assertThat(anterior.getBeneficiarioVinculado()).isSameAs(beneficiarios.get("tienda"));
    }

    @Test
    void duplicarCopiaElVinculoSinCrearNiCambiarBeneficiarios() {
        Beneficiario netflix = Beneficiario.builder()
                .nombre("Netflix").nombreNormalizado("netflix").build();
        Transaccion original = Transaccion.builder()
                .id(TRANSACCION_ID).cuenta(banco).fecha(FECHA).monto(-1000L)
                .beneficiario("Netflix").beneficiarioVinculado(netflix).categoria(comida).build();
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(TRANSACCION_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(original));

        service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID);

        ArgumentCaptor<Transaccion> captura = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).saveAndFlush(captura.capture());
        assertThat(captura.getValue().getBeneficiarioVinculado()).isSameAs(netflix);
        assertThat(captura.getValue().getBeneficiario()).isEqualTo("Netflix");
        assertThat(netflix.getCategoriaPredeterminada()).isNull();
        verify(beneficiarioService, never()).obtenerOCrear(any(), anyString());
    }

    @Test
    void aprobarCambiarEstadoYMoverCuentaNoTocanElBeneficiario() {
        Beneficiario netflix = Beneficiario.builder().nombre("Netflix").build();
        existente.editar(FECHA, -1000L, null, netflix, null);

        service.aprobar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID);
        cambiarEstado(EstadoTransaccion.CONCILIADA);
        service.moverCuenta(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new MoverCuentaRequest(OTRA_CUENTA_ID));

        assertThat(existente.getBeneficiarioVinculado()).isSameAs(netflix);
        assertThat(existente.getBeneficiario()).isEqualTo("Netflix");
        verify(beneficiarioService, never()).obtenerOCrear(any(), anyString());
    }

    // ---------- ayudas ----------

    private TransaccionResponse cambiarEstado(EstadoTransaccion estado) {
        return service.cambiarEstado(
                PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID, new CambiarEstadoRequest(estado));
    }

    private static CrearTransaccionRequest crearSimple(long cuentaId) {
        return new CrearTransaccionRequest(cuentaId, FECHA, -1L, null, null, null, null, null);
    }

    private static ActualizarTransaccionRequest edicion(long monto) {
        return new ActualizarTransaccionRequest(FECHA, monto, null, null, null, null);
    }

    private static FiltroTransacciones filtro() {
        return new FiltroTransacciones(null, null, null, null, null, false, null);
    }

    // ---------- patas de transferencia ----------

    @Test
    void unaPataDeTransferenciaNoSeEditaBorraMueveNiDuplica() {
        existente.enlazarCon(Transaccion.builder().id(41L).cuenta(otraCuenta).build());

        assertThrows(ReglaNegocioException.class, () -> service.actualizar(
                PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new ActualizarTransaccionRequest(FECHA, -5L, null, null, null, null)));
        assertThrows(ReglaNegocioException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID));
        assertThrows(ReglaNegocioException.class, () -> service.moverCuenta(
                PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID, new MoverCuentaRequest(OTRA_CUENTA_ID)));
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.duplicar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(existente.getMonto()).isEqualTo(-1000L);
        assertThat(existente.getCuenta()).isSameAs(banco);
        verify(transaccionRepository, never()).delete(any(Transaccion.class));
        verify(transaccionRepository, never()).saveAndFlush(any(Transaccion.class));
    }

    @Test
    void unaPataDeTransferenciaSiSeApruebaYCambiaDeEstado() {
        existente.enlazarCon(Transaccion.builder().id(41L).cuenta(otraCuenta).build());

        assertThat(service.aprobar(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID).aprobada())
                .isTrue();
        assertThat(service.cambiarEstado(PRESUPUESTO_ID, USUARIO_ID, TRANSACCION_ID,
                new CambiarEstadoRequest(EstadoTransaccion.CONCILIADA)).estado())
                .isEqualTo(EstadoTransaccion.CONCILIADA);
    }
}
