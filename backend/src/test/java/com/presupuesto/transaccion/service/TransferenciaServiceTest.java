package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.presupuesto.transaccion.dto.request.ActualizarTransferenciaRequest;
import com.presupuesto.transaccion.dto.request.CrearTransferenciaRequest;
import com.presupuesto.transaccion.dto.response.TransferenciaResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
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
class TransferenciaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long BANCO_ID = 30L;
    private static final long AHORROS_ID = 31L;
    private static final long EXTERNA_ID = 32L;
    private static final long OTRA_EXTERNA_ID = 33L;
    private static final long CERRADA_ID = 34L;
    private static final long CATEGORIA_ID = 50L;
    private static final long OCULTA_ID = 51L;
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 1);

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private TransferenciaService service;
    private Cuenta banco;
    private Cuenta ahorros;
    private Cuenta externa;
    private Cuenta otraExterna;
    private Cuenta cerrada;
    private Categoria comida;
    private Categoria oculta;

    @BeforeEach
    void preparar() {
        service = new TransferenciaService(
                transaccionRepository,
                new TransaccionReferencias(cuentaRepository, categoriaRepository),
                presupuestoService);
        banco = cuenta(BANCO_ID, true);
        ahorros = cuenta(AHORROS_ID, true);
        externa = cuenta(EXTERNA_ID, false);
        otraExterna = cuenta(OTRA_EXTERNA_ID, false);
        cerrada = cuenta(CERRADA_ID, true);
        cerrada.cerrar();
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        oculta = Categoria.builder().id(OCULTA_ID).build();
        oculta.ocultar();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(OCULTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(oculta));
    }

    private Cuenta cuenta(long id, boolean enPresupuesto) {
        Cuenta cuenta = Cuenta.builder().id(id).enPresupuesto(enPresupuesto).build();
        when(cuentaRepository.findByIdAndPresupuestoId(id, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta));
        return cuenta;
    }

    private TransferenciaResponse crear(long origen, long destino, Long categoriaId) {
        return service.crear(PRESUPUESTO_ID, USUARIO_ID, new CrearTransferenciaRequest(
                origen, destino, FECHA, 30000L, categoriaId, "nota"));
    }

    private Transaccion[] existente(Cuenta origen, Cuenta destino) {
        Transaccion salida = Transaccion.builder()
                .id(1L).cuenta(origen).fecha(FECHA).monto(-30000L).memo("nota").build();
        Transaccion entrada = Transaccion.builder()
                .id(2L).cuenta(destino).fecha(FECHA).monto(30000L).memo("nota").build();
        salida.enlazarCon(entrada);
        entrada.enlazarCon(salida);
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(1L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(salida));
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(2L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(entrada));
        return new Transaccion[] {salida, entrada};
    }

    private static ActualizarTransferenciaRequest edicion(Long categoriaId) {
        return new ActualizarTransferenciaRequest(FECHA.plusDays(2), 45000L, categoriaId, "otro");
    }

    // ---------- crear ----------

    @Test
    void crearEntreDosCuentasDelPresupuestoEnlazaLasPatasSinCategoria() {
        TransferenciaResponse respuesta = crear(BANCO_ID, AHORROS_ID, null);

        assertThat(respuesta.salida().cuentaId()).isEqualTo(BANCO_ID);
        assertThat(respuesta.salida().monto()).isEqualTo(-30000L);
        assertThat(respuesta.entrada().cuentaId()).isEqualTo(AHORROS_ID);
        assertThat(respuesta.entrada().monto()).isEqualTo(30000L);
        assertThat(respuesta.salida().fecha()).isEqualTo(FECHA);
        assertThat(respuesta.entrada().memo()).isEqualTo("nota");
        assertThat(respuesta.salida().categoriaId()).isNull();
        assertThat(respuesta.entrada().categoriaId()).isNull();
        assertThat(respuesta.salida().estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(respuesta.salida().aprobada()).isTrue();
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void crearGuardaLasDosFilasAntesDeEnlazarlas() {
        org.mockito.ArgumentCaptor<java.util.List<Transaccion>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);

        crear(BANCO_ID, AHORROS_ID, null);

        verify(transaccionRepository, org.mockito.Mockito.times(2))
                .saveAllAndFlush(captor.capture());
        Transaccion salida = captor.getValue().get(0);
        Transaccion entrada = captor.getValue().get(1);
        assertThat(salida.getTransaccionPar()).isSameAs(entrada);
        assertThat(entrada.getTransaccionPar()).isSameAs(salida);
    }

    @Test
    void entreCuentasDelPresupuestoConCategoriaDa422() {
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> crear(BANCO_ID, AHORROS_ID, CATEGORIA_ID));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void entreDosExternasSinCategoriaVaSinCategoriaYConCategoriaDa422() {
        TransferenciaResponse respuesta = crear(EXTERNA_ID, OTRA_EXTERNA_ID, null);

        assertThat(respuesta.salida().categoriaId()).isNull();
        assertThat(respuesta.entrada().categoriaId()).isNull();
        assertThrows(ReglaNegocioException.class,
                () -> crear(EXTERNA_ID, OTRA_EXTERNA_ID, CATEGORIA_ID));
    }

    @Test
    void delPresupuestoAUnaExternaExigeCategoriaYLaGuardaSoloEnLaSalida() {
        assertThrows(ReglaNegocioException.class, () -> crear(BANCO_ID, EXTERNA_ID, null));

        TransferenciaResponse respuesta = crear(BANCO_ID, EXTERNA_ID, CATEGORIA_ID);

        assertThat(respuesta.salida().categoriaId()).isEqualTo(CATEGORIA_ID);
        assertThat(respuesta.entrada().categoriaId()).isNull();
    }

    @Test
    void deUnaExternaAlPresupuestoLaCategoriaEsOpcionalYVaSoloEnLaEntrada() {
        TransferenciaResponse sin = crear(EXTERNA_ID, BANCO_ID, null);
        TransferenciaResponse con = crear(EXTERNA_ID, BANCO_ID, CATEGORIA_ID);

        assertThat(sin.salida().categoriaId()).isNull();
        assertThat(sin.entrada().categoriaId()).isNull();
        assertThat(con.salida().categoriaId()).isNull();
        assertThat(con.entrada().categoriaId()).isEqualTo(CATEGORIA_ID);
    }

    @Test
    void unaCategoriaOcultaSirve() {
        TransferenciaResponse respuesta = crear(EXTERNA_ID, BANCO_ID, OCULTA_ID);

        assertThat(respuesta.entrada().categoriaId()).isEqualTo(OCULTA_ID);
    }

    @Test
    void unaCategoriaAjenaDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> crear(BANCO_ID, EXTERNA_ID, 999L));
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void unaCuentaAjenaODeOtroPresupuestoDa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> crear(BANCO_ID, 999L, null));
        assertThrows(RecursoNoEncontradoException.class, () -> crear(999L, BANCO_ID, null));
    }

    @Test
    void origenIgualAlDestinoDa400() {
        NegocioException error = assertThrows(DatosInvalidosException.class,
                () -> crear(BANCO_ID, BANCO_ID, null));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
    }

    @Test
    void conUnaCuentaCerradaDa422() {
        assertThrows(ReglaNegocioException.class, () -> crear(CERRADA_ID, BANCO_ID, null));
        assertThrows(ReglaNegocioException.class, () -> crear(BANCO_ID, CERRADA_ID, null));
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void conPresupuestoAjenoPropagaElErrorYNoConsultaNada() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> crear(BANCO_ID, AHORROS_ID, null));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 1L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, 1L));
        verify(transaccionRepository, never()).findByIdAndCuentaPresupuestoId(any(), any());
    }

    // ---------- obtener ----------

    @Test
    void obtenerPorCualquieraDeLasDosPatasDevuelveLaMismaTransferencia() {
        existente(banco, ahorros);

        TransferenciaResponse porSalida = service.obtener(PRESUPUESTO_ID, USUARIO_ID, 1L);
        TransferenciaResponse porEntrada = service.obtener(PRESUPUESTO_ID, USUARIO_ID, 2L);

        assertThat(porSalida.salida().id()).isEqualTo(1L);
        assertThat(porSalida.entrada().id()).isEqualTo(2L);
        assertThat(porEntrada.salida().id()).isEqualTo(1L);
        assertThat(porEntrada.entrada().id()).isEqualTo(2L);
    }

    @Test
    void obtenerUnaTransaccionNormalOInexistenteDa404() {
        Transaccion normal = Transaccion.builder()
                .id(7L).cuenta(banco).fecha(FECHA).monto(-5L).build();
        when(transaccionRepository.findByIdAndCuentaPresupuestoId(7L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(normal));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 7L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 999L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 7L, edicion(null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, 7L));
    }

    // ---------- actualizar ----------

    @Test
    void actualizarCambiaLasDosPatasYConservaCuentasYEstado() {
        Transaccion[] patas = existente(banco, ahorros);
        patas[0].cambiarEstado(EstadoTransaccion.CONCILIADA);

        TransferenciaResponse respuesta =
                service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 2L, edicion(null));

        assertThat(respuesta.salida().monto()).isEqualTo(-45000L);
        assertThat(respuesta.entrada().monto()).isEqualTo(45000L);
        assertThat(respuesta.salida().fecha()).isEqualTo(FECHA.plusDays(2));
        assertThat(respuesta.entrada().fecha()).isEqualTo(FECHA.plusDays(2));
        assertThat(respuesta.salida().memo()).isEqualTo("otro");
        assertThat(respuesta.entrada().memo()).isEqualTo("otro");
        assertThat(respuesta.salida().cuentaId()).isEqualTo(BANCO_ID);
        assertThat(respuesta.entrada().cuentaId()).isEqualTo(AHORROS_ID);
        assertThat(respuesta.salida().estado()).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(respuesta.entrada().estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
    }

    @Test
    void actualizarCambiaLaCategoriaSoloEnLaPataDelPresupuesto() {
        Transaccion[] patas = existente(banco, externa);
        patas[0].categorizar(comida);
        Categoria ocio = Categoria.builder().id(60L).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(60L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(ocio));

        TransferenciaResponse respuesta =
                service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(60L));

        assertThat(respuesta.salida().categoriaId()).isEqualTo(60L);
        assertThat(respuesta.entrada().categoriaId()).isNull();
    }

    @Test
    void actualizarAplicaLaReglaDeCategoriaYNoCambiaNada() {
        Transaccion[] saleDelPresupuesto = existente(banco, externa);

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(null)));

        assertThat(saleDelPresupuesto[0].getMonto()).isEqualTo(-30000L);
        assertThat(saleDelPresupuesto[1].getMonto()).isEqualTo(30000L);
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void actualizarEntreCuentasDelPresupuestoConCategoriaDa422() {
        existente(banco, ahorros);

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(CATEGORIA_ID)));
    }

    @Test
    void actualizarConCategoriaAjenaDa404() {
        existente(banco, externa);

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(999L)));
    }

    @Test
    void actualizarConUnaPataReconciliadaDa422SinCambiosParciales() {
        Transaccion[] patas = existente(banco, ahorros);
        patas[1].cambiarEstado(EstadoTransaccion.RECONCILIADA);

        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(null)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(patas[0].getMonto()).isEqualTo(-30000L);
        assertThat(patas[1].getMonto()).isEqualTo(30000L);
        assertThat(patas[0].getFecha()).isEqualTo(FECHA);
        verify(transaccionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void actualizarConUnaCuentaCerradaDa422SinCambios() {
        Transaccion[] patas = existente(banco, cerrada);

        assertThrows(ReglaNegocioException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 1L, edicion(null)));

        assertThat(patas[0].getMonto()).isEqualTo(-30000L);
        assertThat(patas[1].getMonto()).isEqualTo(30000L);
    }

    // ---------- borrar ----------

    @Test
    void borrarDesenlazaYQuitaLasDosPatas() {
        Transaccion[] patas = existente(banco, ahorros);

        service.borrar(PRESUPUESTO_ID, USUARIO_ID, 2L);

        assertThat(patas[0].getTransaccionPar()).isNull();
        assertThat(patas[1].getTransaccionPar()).isNull();
        verify(transaccionRepository).deleteAll(java.util.List.of(patas[0], patas[1]));
        verify(transaccionRepository).flush();
    }

    @Test
    void borrarConUnaPataReconciliadaDa422YNoBorraNada() {
        Transaccion[] patas = existente(banco, ahorros);
        patas[0].cambiarEstado(EstadoTransaccion.RECONCILIADA);

        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, 1L));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(patas[0].getTransaccionPar()).isSameAs(patas[1]);
        verify(transaccionRepository, never()).deleteAll(any());
    }
}
