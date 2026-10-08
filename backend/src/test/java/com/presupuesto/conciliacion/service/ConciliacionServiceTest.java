package com.presupuesto.conciliacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.conciliacion.dto.request.CrearConciliacionRequest;
import com.presupuesto.conciliacion.dto.response.ConciliacionResponse;
import com.presupuesto.conciliacion.entity.Conciliacion;
import com.presupuesto.conciliacion.repository.ConciliacionRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.service.SaldoCuentaService;
import com.presupuesto.transaccion.service.TransaccionService;
import java.time.LocalDate;
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
class ConciliacionServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final long CATEGORIA_ID = 50L;
    /** El reloj de pruebas marca 2026-10-02. */
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 10);

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private SaldoCuentaService saldoCuentaService;

    @Mock
    private TransaccionService transaccionService;

    @Mock
    private ConciliacionRepository conciliacionRepository;

    private ConciliacionService service;
    private Presupuesto presupuesto;
    private Cuenta banco;

    @BeforeEach
    void preparar() {
        service = new ConciliacionService(
                presupuestoService,
                cuentaRepository,
                saldoCuentaService,
                transaccionService,
                conciliacionRepository,
                new RelojDePrueba());
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        banco = Cuenta.builder().id(CUENTA_ID).tipo(TipoCuenta.CORRIENTE).build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(saldoCuentaService.saldoConciliadoAl(banco, FECHA)).thenReturn(300_000L);
        when(conciliacionRepository.saveAndFlush(any(Conciliacion.class)))
                .then(returnsFirstArg());
        when(transaccionService.reconciliarHasta(CUENTA_ID, FECHA)).thenReturn(2);
    }

    private ConciliacionResponse crear(CrearConciliacionRequest request) {
        return service.crear(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, request);
    }

    private static CrearConciliacionRequest peticion(
            Long extracto, LocalDate fecha, Boolean ajuste, Long categoriaId) {
        return new CrearConciliacionRequest(extracto, fecha, ajuste, categoriaId);
    }

    private Transaccion ajusteCreado(long id) {
        Transaccion ajuste = Transaccion.builder().id(id).build();
        when(transaccionService.crearAjuste(any(), any(), any(), anyLong(), any()))
                .thenReturn(ajuste);
        return ajuste;
    }

    @Test
    void conDiferenciaCeroReconcilaSinCrearAjuste() {
        ConciliacionResponse respuesta = crear(peticion(300_000L, FECHA, null, null));

        assertThat(respuesta.ajuste()).isZero();
        assertThat(respuesta.transaccionAjusteId()).isNull();
        assertThat(respuesta.cantidadReconciliadas()).isEqualTo(2);
        verify(transaccionService, never()).crearAjuste(any(), any(), any(), anyLong(), any());
    }

    @Test
    void pedirAjusteSinDiferenciaNoCreaNada() {
        crear(peticion(300_000L, FECHA, true, null));

        verify(transaccionService, never()).crearAjuste(any(), any(), any(), anyLong(), any());
    }

    @Test
    void conDiferenciaPositivaYAjusteLoCreaPorLaDiferenciaConLaFechaDelExtracto() {
        ajusteCreado(77L);

        ConciliacionResponse respuesta = crear(peticion(310_000L, FECHA, true, null));

        assertThat(respuesta.ajuste()).isEqualTo(10_000L);
        assertThat(respuesta.transaccionAjusteId()).isEqualTo(77L);
        verify(transaccionService).crearAjuste(presupuesto, banco, FECHA, 10_000L, null);
        verify(transaccionService).reconciliarHasta(CUENTA_ID, FECHA);
    }

    @Test
    void conDiferenciaSinPedirAjusteDa422SinCambiarNada() {
        assertThrows(ReglaNegocioException.class, () -> crear(peticion(310_000L, FECHA, null, null)));
        assertThrows(ReglaNegocioException.class,
                () -> crear(peticion(310_000L, FECHA, false, null)));

        verify(transaccionService, never()).reconciliarHasta(anyLong(), any());
        verify(conciliacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void unAjusteNegativoEnElPresupuestoSinCategoriaDa422() {
        assertThrows(ReglaNegocioException.class,
                () -> crear(peticion(290_000L, FECHA, true, null)));

        verify(transaccionService, never()).crearAjuste(any(), any(), any(), anyLong(), any());
        verify(conciliacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void unAjusteNegativoConCategoriaSeCrea() {
        ajusteCreado(77L);

        ConciliacionResponse respuesta = crear(peticion(290_000L, FECHA, true, CATEGORIA_ID));

        assertThat(respuesta.ajuste()).isEqualTo(-10_000L);
        verify(transaccionService).crearAjuste(presupuesto, banco, FECHA, -10_000L, CATEGORIA_ID);
    }

    @Test
    void unAjustePositivoEnTarjetaSinCategoriaDa422() {
        Cuenta visa = Cuenta.builder().id(CUENTA_ID).tipo(TipoCuenta.TARJETA_CREDITO).build();
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(visa));
        when(saldoCuentaService.saldoConciliadoAl(visa, FECHA)).thenReturn(300_000L);

        assertThrows(ReglaNegocioException.class,
                () -> crear(peticion(310_000L, FECHA, true, null)));
    }

    @Test
    void unaCuentaFueraDelPresupuestoNoAdmiteCategoriaPeroAdmiteAjusteNegativo() {
        Cuenta externa = Cuenta.builder()
                .id(CUENTA_ID).tipo(TipoCuenta.CORRIENTE).enPresupuesto(false).build();
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(externa));
        when(saldoCuentaService.saldoConciliadoAl(externa, FECHA)).thenReturn(300_000L);
        ajusteCreado(77L);

        assertThrows(ReglaNegocioException.class,
                () -> crear(peticion(290_000L, FECHA, true, CATEGORIA_ID)));
        assertThat(crear(peticion(290_000L, FECHA, true, null)).ajuste()).isEqualTo(-10_000L);
    }

    @Test
    void unaCuentaCerradaDa422() {
        Cuenta cerrada = Cuenta.builder().id(CUENTA_ID).tipo(TipoCuenta.CORRIENTE).build();
        cerrada.cerrar();
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cerrada));

        assertThrows(ReglaNegocioException.class,
                () -> crear(peticion(300_000L, FECHA, null, null)));
        verify(conciliacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void laFechaDeHoyEsValidaYLaDeMananaDa400() {
        LocalDate hoy = LocalDate.of(2026, 10, 2);
        when(saldoCuentaService.saldoConciliadoAl(banco, hoy)).thenReturn(300_000L);

        assertThat(crear(peticion(300_000L, hoy, null, null)).fecha()).isEqualTo(hoy);
        assertThrows(DatosInvalidosException.class,
                () -> crear(peticion(300_000L, hoy.plusDays(1), null, null)));
    }

    @Test
    void faltaDeSaldoOFechaDa400() {
        assertThrows(DatosInvalidosException.class, () -> crear(peticion(null, FECHA, null, null)));
        assertThrows(DatosInvalidosException.class,
                () -> crear(peticion(300_000L, null, null, null)));
    }

    @Test
    void laCuentaAjenaDa404AntesQueUnCuerpoIncompleto() {
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> crear(peticion(null, null, null, null)));
    }

    @Test
    void elPresupuestoAjenoDa404SinTocarLaCuenta() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> crear(peticion(300_000L, FECHA, null, null)));
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    void elEstadoNoBloqueaLaCuentaYCalculaLaDiferenciaAlCorte() {
        when(saldoCuentaService.saldoConciliadoAl(banco, SaldoCuentaService.SIN_LIMITE))
                .thenReturn(250_000L);
        when(transaccionService.contarNoConciliadas(CUENTA_ID)).thenReturn(4L);
        when(transaccionService.listarNoConciliadas(CUENTA_ID, 100)).thenReturn(java.util.List.of());

        var estado = service.estado(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, 300_000L, FECHA);

        assertThat(estado.saldoConciliado()).isEqualTo(250_000L);
        assertThat(estado.saldoConciliadoAlCorte()).isEqualTo(300_000L);
        assertThat(estado.diferencia()).isZero();
        assertThat(estado.totalNoConciliadas()).isEqualTo(4L);
        verify(cuentaRepository, never()).findByIdAndPresupuestoIdParaActualizar(anyLong(), anyLong());
    }

    @Test
    void elEstadoValidaLosParametrosDespuesDeLaCuenta() {
        assertThrows(DatosInvalidosException.class,
                () -> service.estado(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, null, FECHA));
        assertThrows(DatosInvalidosException.class,
                () -> service.estado(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, 1L, null));
        assertThrows(DatosInvalidosException.class,
                () -> service.estado(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, 1L,
                        LocalDate.of(2026, 10, 3)));
    }
}
