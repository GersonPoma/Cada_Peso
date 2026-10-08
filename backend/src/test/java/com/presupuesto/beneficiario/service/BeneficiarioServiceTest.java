package com.presupuesto.beneficiario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.beneficiario.dto.request.ActualizarBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.request.CrearBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.response.BeneficiarioResponse;
import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.repository.BeneficiarioRepository;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeneficiarioServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long BENEFICIARIO_ID = 30L;
    private static final long CATEGORIA_ID = 40L;

    @Mock
    private BeneficiarioRepository beneficiarioRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private BeneficiarioService service;
    private Presupuesto presupuesto;
    private Categoria ocio;
    private Beneficiario existente;

    @BeforeEach
    void preparar() {
        service = new BeneficiarioService(
                beneficiarioRepository, categoriaRepository, presupuestoService);
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        ocio = Categoria.builder().id(CATEGORIA_ID).build();
        existente = Beneficiario.builder()
                .id(BENEFICIARIO_ID).presupuesto(presupuesto)
                .nombre("Netflix").nombreNormalizado("netflix").categoriaPredeterminada(ocio)
                .build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(ocio));
        when(beneficiarioRepository.findByIdAndPresupuestoId(BENEFICIARIO_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(existente));
        when(beneficiarioRepository.saveAndFlush(any(Beneficiario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ---------- crear ----------

    @Test
    void crearGuardaNombreNormalizadoPresupuestoYCategoria() {
        BeneficiarioResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearBeneficiarioRequest("Tienda DON Pepe", CATEGORIA_ID));

        ArgumentCaptor<Beneficiario> captura = ArgumentCaptor.forClass(Beneficiario.class);
        verify(beneficiarioRepository).saveAndFlush(captura.capture());
        assertThat(captura.getValue().getPresupuesto()).isSameAs(presupuesto);
        assertThat(captura.getValue().getNombreNormalizado()).isEqualTo("tienda don pepe");
        assertThat(captura.getValue().getCategoriaPredeterminada()).isSameAs(ocio);
        assertThat(respuesta.nombre()).isEqualTo("Tienda DON Pepe");
        assertThat(respuesta.categoriaPredeterminadaId()).isEqualTo(CATEGORIA_ID);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void crearSinCategoriaDejaLaCategoriaPredeterminadaNula() {
        BeneficiarioResponse respuesta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearBeneficiarioRequest("Tienda", null));

        assertThat(respuesta.categoriaPredeterminadaId()).isNull();
        verify(categoriaRepository, never()).findByIdAndGrupoPresupuestoId(anyLong(), anyLong());
    }

    @Test
    void crearConUnNombreRepetidoDa409SinGuardar() {
        when(beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "netflix")).thenReturn(true);

        ConflictoException error = assertThrows(ConflictoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearBeneficiarioRequest("NETFLIX", null)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.BENEFICIARIO_YA_EXISTE);
        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearConCarreraDeUnicidadTraduceLaViolacionA409() {
        when(beneficiarioRepository.saveAndFlush(any(Beneficiario.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        ConflictoException error = assertThrows(ConflictoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearBeneficiarioRequest("Nuevo", null)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.BENEFICIARIO_YA_EXISTE);
    }

    @Test
    void crearConCategoriaAjenaDa404SinGuardar() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearBeneficiarioRequest("Tienda", 999L)));

        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearConPresupuestoAjenoPropagaElErrorYNoGuardaNada() {
        when(presupuestoService.obtenerDelUsuario(99L, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                99L, USUARIO_ID, new CrearBeneficiarioRequest("Tienda", null)));

        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    // ---------- listar ----------

    @Test
    void listarSinQDevuelveTodosOrdenadosYNoUsaLaBusqueda() {
        when(beneficiarioRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(existente));

        List<BeneficiarioResponse> lista = service.listar(PRESUPUESTO_ID, USUARIO_ID, null, null);

        assertThat(lista).extracting(BeneficiarioResponse::nombre).containsExactly("Netflix");
        verify(beneficiarioRepository, never()).buscarPorPrefijo(anyLong(), anyString(), any());
    }

    @Test
    void listarConQVaciaOSoloEspaciosSeTrataComoAusente() {
        when(beneficiarioRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(existente));

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID, "", null)).hasSize(1);
        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID, "   ", 5)).hasSize(1);
        verify(beneficiarioRepository, never()).buscarPorPrefijo(anyLong(), anyString(), any());
    }

    @Test
    void listarConQBuscaPorPrefijoEnMinusculasConElLimitePorDefecto() {
        when(beneficiarioRepository.buscarPorPrefijo(eq(PRESUPUESTO_ID), anyString(), any()))
                .thenReturn(List.of(existente));

        service.listar(PRESUPUESTO_ID, USUARIO_ID, "  NeT ", null);

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(beneficiarioRepository)
                .buscarPorPrefijo(eq(PRESUPUESTO_ID), eq("net%"), limite.capture());
        assertThat(limite.getValue().getPageNumber()).isZero();
        assertThat(limite.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void listarConQYLimiteExplicitoLoAplica() {
        service.listar(PRESUPUESTO_ID, USUARIO_ID, "a", 50);

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(beneficiarioRepository).buscarPorPrefijo(eq(PRESUPUESTO_ID), eq("a%"),
                limite.capture());
        assertThat(limite.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void elPatronEscapaLosComodinesYElCaracterDeEscape() {
        assertThat(BeneficiarioService.patronDePrefijo("100%")).isEqualTo("100!%%");
        assertThat(BeneficiarioService.patronDePrefijo("a_B")).isEqualTo("a!_b%");
        assertThat(BeneficiarioService.patronDePrefijo("wow!")).isEqualTo("wow!!%");
        assertThat(BeneficiarioService.patronDePrefijo("Ab")).isEqualTo("ab%");
    }

    @Test
    void listarConLimiteFueraDeRangoDa400AunSinQ() {
        for (int limite : new int[] {0, -1, 51}) {
            assertThrows(DatosInvalidosException.class,
                    () -> service.listar(PRESUPUESTO_ID, USUARIO_ID, "a", limite));
            assertThrows(DatosInvalidosException.class,
                    () -> service.listar(PRESUPUESTO_ID, USUARIO_ID, null, limite));
        }
    }

    @Test
    void listarAceptaLosLimitesDelRango() {
        service.listar(PRESUPUESTO_ID, USUARIO_ID, "a", 1);
        service.listar(PRESUPUESTO_ID, USUARIO_ID, "a", 50);

        verify(beneficiarioRepository, org.mockito.Mockito.times(2))
                .buscarPorPrefijo(eq(PRESUPUESTO_ID), eq("a%"), any());
    }

    // ---------- obtener ----------

    @Test
    void obtenerDevuelveElBeneficiario() {
        BeneficiarioResponse respuesta =
                service.obtener(PRESUPUESTO_ID, USUARIO_ID, BENEFICIARIO_ID);

        assertThat(respuesta).isEqualTo(new BeneficiarioResponse(
                BENEFICIARIO_ID, "Netflix", CATEGORIA_ID));
    }

    @Test
    void cadaOperacionDa404ConPresupuestoAjenoYConBeneficiarioAjeno() {
        when(presupuestoService.obtenerDelUsuario(99L, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));
        ActualizarBeneficiarioRequest edicion = new ActualizarBeneficiarioRequest("X", null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.listar(99L, USUARIO_ID, null, null));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(99L, USUARIO_ID, BENEFICIARIO_ID));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(99L, USUARIO_ID, BENEFICIARIO_ID, edicion));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 999L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 999L, edicion));
    }

    // ---------- actualizar ----------

    @Test
    void actualizarRenombraYCambiaLaCategoria() {
        existente.cambiarCategoriaPredeterminada(null);

        BeneficiarioResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                BENEFICIARIO_ID,
                new ActualizarBeneficiarioRequest("Netflix Bolivia", CATEGORIA_ID));

        assertThat(respuesta.nombre()).isEqualTo("Netflix Bolivia");
        assertThat(existente.getNombreNormalizado()).isEqualTo("netflix bolivia");
        assertThat(respuesta.categoriaPredeterminadaId()).isEqualTo(CATEGORIA_ID);
    }

    @Test
    void actualizarConCategoriaNulaLaQuita() {
        BeneficiarioResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                BENEFICIARIO_ID, new ActualizarBeneficiarioRequest("Netflix", null));

        assertThat(respuesta.categoriaPredeterminadaId()).isNull();
    }

    @Test
    void actualizarConservandoSuPropioNombreConOtraCapitalizacionEsValido() {
        existente.renombrar("netflix");

        BeneficiarioResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                BENEFICIARIO_ID, new ActualizarBeneficiarioRequest("Netflix", CATEGORIA_ID));

        assertThat(respuesta.nombre()).isEqualTo("Netflix");
        verify(beneficiarioRepository).existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                PRESUPUESTO_ID, "netflix", BENEFICIARIO_ID);
    }

    @Test
    void actualizarAlNombreDeOtroBeneficiarioDa409SinCambiarNada() {
        when(beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                PRESUPUESTO_ID, "spotify", BENEFICIARIO_ID)).thenReturn(true);

        ConflictoException error = assertThrows(ConflictoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, BENEFICIARIO_ID,
                        new ActualizarBeneficiarioRequest("Spotify", null)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.BENEFICIARIO_YA_EXISTE);
        assertThat(existente.getNombre()).isEqualTo("Netflix");
        assertThat(existente.getCategoriaPredeterminada()).isSameAs(ocio);
        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizarConCategoriaAjenaDa404SinCambiarNada() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, BENEFICIARIO_ID,
                        new ActualizarBeneficiarioRequest("Otro", 999L)));

        assertThat(existente.getNombre()).isEqualTo("Netflix");
    }

    // ---------- obtenerOCrear ----------

    @Test
    void obtenerOCrearReutilizaElDelMismoNombreSinDistinguirMayusculas() {
        when(beneficiarioRepository.findByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "netflix")).thenReturn(Optional.of(existente));

        Beneficiario resultado = service.obtenerOCrear(presupuesto, "NETFLIX");

        assertThat(resultado).isSameAs(existente);
        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void obtenerOCrearCreaUnoNuevoSinCategoriaSiNoExiste() {
        when(beneficiarioRepository.findByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "spotify")).thenReturn(Optional.empty());

        Beneficiario resultado = service.obtenerOCrear(presupuesto, "Spotify");

        assertThat(resultado.getNombre()).isEqualTo("Spotify");
        assertThat(resultado.getNombreNormalizado()).isEqualTo("spotify");
        assertThat(resultado.getPresupuesto()).isSameAs(presupuesto);
        assertThat(resultado.getCategoriaPredeterminada()).isNull();
        verify(beneficiarioRepository).saveAndFlush(resultado);
    }

    @Test
    void obtenerOCrearConCarreraDeUnicidadDa409() {
        when(beneficiarioRepository.findByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "spotify")).thenReturn(Optional.empty());
        when(beneficiarioRepository.saveAndFlush(any(Beneficiario.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        ConflictoException error = assertThrows(ConflictoException.class,
                () -> service.obtenerOCrear(presupuesto, "Spotify"));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.BENEFICIARIO_YA_EXISTE);
    }

    // ---------- categoría de pago de tarjeta ----------

    private static final long PAGO_ID = 41L;

    private void hayCategoriaDePago() {
        Categoria pago = Categoria.builder().id(PAGO_ID)
                .cuentaTarjeta(Cuenta.builder().id(44L).build()).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(PAGO_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(pago));
    }

    @Test
    void crearConUnaCategoriaDePagoDa422SinGuardar() {
        hayCategoriaDePago();

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearBeneficiarioRequest("Tienda", PAGO_ID)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizarConUnaCategoriaDePagoDa422YConservaNombreYCategoria() {
        hayCategoriaDePago();

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, BENEFICIARIO_ID,
                        new ActualizarBeneficiarioRequest("Otro nombre", PAGO_ID)));

        assertThat(existente.getNombre()).isEqualTo("Netflix");
        assertThat(existente.getCategoriaPredeterminada()).isSameAs(ocio);
        verify(beneficiarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void quitarLaCategoriaSigueSiendoValidoAunqueExistaUnaDePago() {
        hayCategoriaDePago();

        BeneficiarioResponse respuesta = service.actualizar(PRESUPUESTO_ID, USUARIO_ID,
                BENEFICIARIO_ID, new ActualizarBeneficiarioRequest("Netflix", null));

        assertThat(respuesta.categoriaPredeterminadaId()).isNull();
    }
}
