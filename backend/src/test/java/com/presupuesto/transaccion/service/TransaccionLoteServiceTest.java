package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.LoteRequest;
import com.presupuesto.transaccion.dto.request.OperacionLote;
import com.presupuesto.transaccion.dto.response.LoteResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
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
class TransaccionLoteServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CATEGORIA_ID = 50L;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private TransaccionLoteService service;
    private Categoria comida;
    private Transaccion a;
    private Transaccion b;

    @BeforeEach
    void preparar() {
        service = new TransaccionLoteService(
                transaccionRepository,
                new TransaccionReferencias(cuentaRepository, categoriaRepository),
                presupuestoService);
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        a = nueva(1L);
        b = nueva(2L);
        devolver(a, b);
    }

    @Test
    void categorizarAplicaLaCategoriaATodasYDevuelveLaCantidad() {
        LoteResponse respuesta = ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L);

        assertThat(respuesta.afectadas()).isEqualTo(2);
        assertThat(a.getCategoria()).isSameAs(comida);
        assertThat(b.getCategoria()).isSameAs(comida);
        verify(transaccionRepository).saveAllAndFlush(List.of(a, b));
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void categorizarRecuerdaLaCategoriaDeLosBeneficiariosVinculados() {
        Beneficiario netflix = Beneficiario.builder().nombre("Netflix").build();
        Beneficiario spotify = Beneficiario.builder().nombre("Spotify").build();
        Transaccion c = nueva(3L);
        a.editar(a.getFecha(), a.getMonto(), null, netflix, null);
        b.editar(b.getFecha(), b.getMonto(), null, spotify, null);
        devolver(a, b, c);

        ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L, 3L);

        assertThat(netflix.getCategoriaPredeterminada()).isSameAs(comida);
        assertThat(spotify.getCategoriaPredeterminada()).isSameAs(comida);
        assertThat(c.getCategoria()).isSameAs(comida);
        assertThat(c.getBeneficiarioVinculado()).isNull();
    }

    @Test
    void unLoteRechazadoNoTocaLaCategoriaDeLosBeneficiarios() {
        Beneficiario netflix = Beneficiario.builder().nombre("Netflix").build();
        a.editar(a.getFecha(), a.getMonto(), null, netflix, null);
        b.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-400L).build(),
                SubTransaccion.builder().monto(-600L).build()));

        assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L));

        assertThat(netflix.getCategoriaPredeterminada()).isNull();
    }

    @Test
    void aprobarYBorrarNoTocanLaCategoriaDeLosBeneficiarios() {
        Beneficiario netflix = Beneficiario.builder().nombre("Netflix").build();
        a.editar(a.getFecha(), a.getMonto(), null, netflix, null);

        ejecutar(OperacionLote.APROBAR, null, 1L);
        ejecutar(OperacionLote.BORRAR, null, 1L);

        assertThat(netflix.getCategoriaPredeterminada()).isNull();
    }

    @Test
    void categorizarSinCategoriaDa400YNoCambiaNada() {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, null, 1L, 2L));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        assertThat(a.getCategoria()).isNull();
    }

    @Test
    void categorizarConCategoriaAjenaDa404YNoCambiaNada() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, 999L, 1L, 2L));

        assertThat(a.getCategoria()).isNull();
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void categorizarUnaDivididaDa422YNoAplicaNada() {
        b.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-500L).build(),
                SubTransaccion.builder().monto(-500L).build()));

        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(a.getCategoria()).isNull();
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void categorizarOBorrarUnaReconciliadaDa422YNoAplicaNada() {
        b.cambiarEstado(EstadoTransaccion.RECONCILIADA);

        assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L));
        assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.BORRAR, null, 1L, 2L));

        assertThat(a.getCategoria()).isNull();
        verify(transaccionRepository, never()).deleteAll(anyCollection());
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void aprobarApruebaTodasIncluidaUnaReconciliada() {
        Transaccion sinAprobar = Transaccion.builder()
                .id(1L).cuenta(Cuenta.builder().build()).fecha(LocalDate.of(2026, 9, 1))
                .monto(-1L).aprobada(false).build();
        Transaccion reconciliada = Transaccion.builder()
                .id(2L).cuenta(Cuenta.builder().build()).fecha(LocalDate.of(2026, 9, 1))
                .monto(-1L).aprobada(false).estado(EstadoTransaccion.RECONCILIADA).build();
        devolver(sinAprobar, reconciliada);

        LoteResponse respuesta = ejecutar(OperacionLote.APROBAR, null, 1L, 2L);

        assertThat(respuesta.afectadas()).isEqualTo(2);
        assertThat(sinAprobar.isAprobada()).isTrue();
        assertThat(reconciliada.isAprobada()).isTrue();
        assertThat(reconciliada.estaReconciliada()).isTrue();
        verify(transaccionRepository).saveAllAndFlush(List.of(sinAprobar, reconciliada));
    }

    @Test
    void borrarEliminaTodasYDevuelveLaCantidad() {
        LoteResponse respuesta = ejecutar(OperacionLote.BORRAR, null, 1L, 2L);

        assertThat(respuesta.afectadas()).isEqualTo(2);
        verify(transaccionRepository).deleteAll(List.of(a, b));
    }

    @Test
    void unIdAjenoOInexistenteDa404EnCualquierOperacionYNoAplicaNada() {
        for (OperacionLote operacion : OperacionLote.values()) {
            assertThrows(RecursoNoEncontradoException.class,
                    () -> ejecutar(operacion, CATEGORIA_ID, 1L, 2L, 3L));
        }

        verify(transaccionRepository, never()).saveAllAndFlush(any());
        verify(transaccionRepository, never()).deleteAll(anyCollection());
        assertThat(a.getCategoria()).isNull();
    }

    @Test
    void losIdsRepetidosCuentanUnaSolaVez() {
        LoteResponse respuesta = ejecutar(OperacionLote.APROBAR, null, 1L, 1L, 2L, 2L);

        assertThat(respuesta.afectadas()).isEqualTo(2);
    }

    @Test
    void conPresupuestoAjenoPropagaElErrorYNoConsultaTransacciones() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> ejecutar(OperacionLote.APROBAR, null, 1L));
        verify(transaccionRepository, never())
                .findByIdInAndCuentaPresupuestoId(anyCollection(), any());
    }

    private LoteResponse ejecutar(OperacionLote operacion, Long categoriaId, Long... ids) {
        return service.ejecutar(
                PRESUPUESTO_ID, USUARIO_ID, new LoteRequest(List.of(ids), operacion, categoriaId));
    }

    private void devolver(Transaccion... transacciones) {
        when(transaccionRepository.findByIdInAndCuentaPresupuestoId(anyCollection(), any()))
                .thenAnswer(inv -> {
                    java.util.Collection<Long> pedidos = inv.getArgument(0);
                    return List.of(transacciones).stream()
                            .filter(t -> pedidos.contains(t.getId()))
                            .toList();
                });
    }

    private static Transaccion nueva(long id) {
        return Transaccion.builder()
                .id(id).cuenta(Cuenta.builder().build()).fecha(LocalDate.of(2026, 9, 1))
                .monto(-1000L).build();
    }

    @Test
    void borrarOCategorizarConUnaPataDeTransferenciaDa422YNoAplicaNada() {
        b.enlazarCon(nueva(3L));

        assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.BORRAR, null, 1L, 2L));
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, CATEGORIA_ID, 1L, 2L));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(a.getCategoria()).isNull();
        verify(transaccionRepository, never()).deleteAll(anyCollection());
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void aprobarUnaPataDeTransferenciaEstaPermitido() {
        Transaccion pata = Transaccion.builder()
                .id(1L).cuenta(Cuenta.builder().build()).fecha(LocalDate.of(2026, 9, 1))
                .monto(-1L).aprobada(false).build();
        pata.enlazarCon(nueva(9L));
        devolver(pata);

        LoteResponse respuesta = ejecutar(OperacionLote.APROBAR, null, 1L);

        assertThat(respuesta.afectadas()).isEqualTo(1);
        assertThat(pata.isAprobada()).isTrue();
    }

    @Test
    void categorizarConUnaCategoriaDePagoDa422YNoCambiaNingunaTransaccion() {
        Categoria pago = Categoria.builder().id(60L)
                .cuentaTarjeta(Cuenta.builder().id(44L).build()).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(60L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(pago));

        assertThrows(ReglaNegocioException.class,
                () -> ejecutar(OperacionLote.CATEGORIZAR, 60L, 1L, 2L));

        assertThat(a.getCategoria()).isNull();
        assertThat(b.getCategoria()).isNull();
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }
}
