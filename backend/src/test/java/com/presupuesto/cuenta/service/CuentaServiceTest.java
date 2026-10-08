package com.presupuesto.cuenta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuentaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private CuentaService service;
    private Presupuesto presupuesto;

    @BeforeEach
    void preparar() {
        service = new CuentaService(cuentaRepository, presupuestoService, eventos);
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(cuentaRepository.saveAndFlush(any(Cuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void crearGuardaNombreNormalizadoPresupuestoYValoresPorDefecto() {
        CuentaResponse response = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCuentaRequest("Mi BANCO", TipoCuenta.CORRIENTE, null, null));

        ArgumentCaptor<Cuenta> captor = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPresupuesto()).isSameAs(presupuesto);
        assertThat(captor.getValue().getNombreNormalizado()).isEqualTo("mi banco");
        assertThat(response.nombre()).isEqualTo("Mi BANCO");
        assertThat(response.enPresupuesto()).isTrue();
        assertThat(response.saldoInicial()).isZero();
        assertThat(response.cerrada()).isFalse();
    }

    @Test
    void crearConSaldoNegativoEnTarjetaOPrestamoEsValido() {
        CuentaResponse tarjeta = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCuentaRequest("Tarjeta", TipoCuenta.TARJETA_CREDITO, null, -250_000L));
        CuentaResponse prestamo = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCuentaRequest("Deuda", TipoCuenta.PRESTAMO, null, -1L));

        assertThat(tarjeta.saldoInicial()).isEqualTo(-250_000L);
        assertThat(prestamo.saldoInicial()).isEqualTo(-1L);
    }

    @Test
    void crearConSaldoNegativoEnOtrosTiposLanzaReglaDeNegocio() {
        for (TipoCuenta tipo : List.of(TipoCuenta.CORRIENTE, TipoCuenta.AHORRO,
                TipoCuenta.EFECTIVO, TipoCuenta.INVERSION)) {
            ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                    () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                            new CrearCuentaRequest("Cuenta", tipo, null, -1L)));
            assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        }
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearConUnNombreRepetidoLanzaConflictoSinGuardar() {
        when(cuentaRepository.existsByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "banco")).thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearCuentaRequest("BANCO", TipoCuenta.CORRIENTE, null, null)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CUENTA_YA_EXISTE);
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaSeTraduceAConflicto() {
        when(cuentaRepository.saveAndFlush(any(Cuenta.class)))
                .thenThrow(new DataIntegrityViolationException("uk_cuentas_presupuesto_nombre"));

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearCuentaRequest("Banco", TipoCuenta.CORRIENTE, null, null)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CUENTA_YA_EXISTE);
    }

    @Test
    void unPresupuestoAjenoPropagaElNoEncontradoEnTodasLasOperaciones() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, 99L))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(PRESUPUESTO_ID, 99L,
                new CrearCuentaRequest("Banco", TipoCuenta.CORRIENTE, null, null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.listar(PRESUPUESTO_ID, 99L, false));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, 99L, CUENTA_ID));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                PRESUPUESTO_ID, 99L, CUENTA_ID,
                new ActualizarCuentaRequest("Banco", TipoCuenta.AHORRO)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.cerrar(PRESUPUESTO_ID, 99L, CUENTA_ID));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.reabrir(PRESUPUESTO_ID, 99L, CUENTA_ID));
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaCuentaInexistenteODeOtroPresupuestoLanzaNoEncontrado() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                new ActualizarCuentaRequest("Banco", TipoCuenta.AHORRO)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.cerrar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.reabrir(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID));
    }

    @Test
    void listarSinIncluirCerradasUsaLaConsultaDeAbiertas() {
        when(cuentaRepository.findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado(
                PRESUPUESTO_ID)).thenReturn(List.of(cuenta("Banco", TipoCuenta.CORRIENTE, 0L)));

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID, false))
                .extracting(CuentaResponse::nombre).containsExactly("Banco");
        verify(cuentaRepository, never())
                .findByPresupuestoIdOrderByNombreNormalizado(any());
    }

    @Test
    void listarIncluyendoCerradasUsaLaConsultaCompleta() {
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(cuenta("Banco", TipoCuenta.CORRIENTE, 0L)));

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID, true)).hasSize(1);
    }

    @Test
    void actualizarCambiaNombreYTipoYConservaSaldoInicialYEnPresupuesto() {
        Cuenta existente = cuenta("Banco", TipoCuenta.CORRIENTE, 1_000L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(existente));

        CuentaResponse response = service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                new ActualizarCuentaRequest("Ahorros", TipoCuenta.AHORRO));

        assertThat(response.nombre()).isEqualTo("Ahorros");
        assertThat(existente.getNombreNormalizado()).isEqualTo("ahorros");
        assertThat(response.tipo()).isEqualTo(TipoCuenta.AHORRO);
        assertThat(response.saldoInicial()).isEqualTo(1_000L);
        assertThat(response.enPresupuesto()).isTrue();
    }

    @Test
    void actualizarConElNombreDeOtraCuentaLanzaConflicto() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta("Efectivo", TipoCuenta.EFECTIVO, 0L)));
        when(cuentaRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                PRESUPUESTO_ID, "banco", CUENTA_ID)).thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                        new ActualizarCuentaRequest("Banco", TipoCuenta.EFECTIVO)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CUENTA_YA_EXISTE);
    }

    @Test
    void cambiarElTipoConSaldoNegativoAUnoQueNoLoAdmiteLanzaReglaYNoCambiaNada() {
        Cuenta tarjeta = cuenta("Tarjeta", TipoCuenta.TARJETA_CREDITO, -500L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(tarjeta));

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                        new ActualizarCuentaRequest("Tarjeta", TipoCuenta.AHORRO)));

        assertThat(tarjeta.getTipo()).isEqualTo(TipoCuenta.TARJETA_CREDITO);
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarElTipoDeUnaTarjetaAOtroQueAdmiteSaldoNegativoLanzaReglaYNoCambiaNada() {
        Cuenta tarjeta = cuenta("Tarjeta", TipoCuenta.TARJETA_CREDITO, -500L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(tarjeta));

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                        new ActualizarCuentaRequest("Tarjeta", TipoCuenta.PRESTAMO)));

        assertThat(tarjeta.getTipo()).isEqualTo(TipoCuenta.TARJETA_CREDITO);
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void cerrarYReabrirSonIdempotentes() {
        Cuenta existente = cuenta("Banco", TipoCuenta.CORRIENTE, 0L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(existente));

        assertThat(service.cerrar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID).cerrada()).isTrue();
        assertThat(service.cerrar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID).cerrada()).isTrue();
        assertThat(service.reabrir(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID).cerrada()).isFalse();
        assertThat(service.reabrir(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID).cerrada()).isFalse();
    }

    @Test
    void cambiarUnaCuentaATarjetaDeCreditoLanzaReglaYNoCambiaNada() {
        Cuenta corriente = cuenta("Banco", TipoCuenta.CORRIENTE, 0L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(corriente));

        ReglaNegocioException excepcion = assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                        new ActualizarCuentaRequest("Banco", TipoCuenta.TARJETA_CREDITO)));

        assertThat(excepcion.getMessage()).isEqualTo(CuentaService.MENSAJE_TIPO_TARJETA);
        assertThat(corriente.getTipo()).isEqualTo(TipoCuenta.CORRIENTE);
        verify(cuentaRepository, never()).saveAndFlush(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void renombrarUnaTarjetaConservandoElTipoEsValido() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta("Visa", TipoCuenta.TARJETA_CREDITO, 0L)));

        CuentaResponse response = service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                new ActualizarCuentaRequest("Visa Oro", TipoCuenta.TARJETA_CREDITO));

        assertThat(response.nombre()).isEqualTo("Visa Oro");
        assertThat(response.tipo()).isEqualTo(TipoCuenta.TARJETA_CREDITO);
    }

    @Test
    void crearPublicaLaCuentaCreadaYSiFallaElGuardadoNoPublicaNada() {
        service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCuentaRequest("Visa", TipoCuenta.TARJETA_CREDITO, null, null));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CuentaCreadaEvento.class);
        assertThat(((CuentaCreadaEvento) captor.getValue()).cuenta().getNombre())
                .isEqualTo("Visa");
    }

    @Test
    void crearConUnNombreRepetidoNoPublicaElEvento() {
        when(cuentaRepository.existsByPresupuestoIdAndNombreNormalizado(PRESUPUESTO_ID, "visa"))
                .thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCuentaRequest("Visa", TipoCuenta.TARJETA_CREDITO, null, null)));

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void actualizarPublicaLaRenombradaSoloSiElNombreCambio() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta("Visa", TipoCuenta.TARJETA_CREDITO, 0L)));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                new ActualizarCuentaRequest("Visa", TipoCuenta.TARJETA_CREDITO));
        verify(eventos, never()).publishEvent(any(Object.class));

        service.actualizar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                new ActualizarCuentaRequest("VISA", TipoCuenta.TARJETA_CREDITO));
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CuentaRenombradaEvento.class);
    }

    @Test
    void cerrarYReabrirPublicanSusEventos() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta("Visa", TipoCuenta.TARJETA_CREDITO, 0L)));

        service.cerrar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID);
        service.reabrir(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(0)).isInstanceOf(CuentaCerradaEvento.class);
        assertThat(captor.getAllValues().get(1)).isInstanceOf(CuentaReabiertaEvento.class);
    }

    private Cuenta cuenta(String nombre, TipoCuenta tipo, long saldoInicial) {
        return Cuenta.builder()
                .id(CUENTA_ID)
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(tipo)
                .saldoInicial(saldoInicial)
                .build();
    }
}
