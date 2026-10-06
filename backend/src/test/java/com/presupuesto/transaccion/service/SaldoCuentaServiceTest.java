package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.response.SaldoCuentaResponse;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.repository.TransaccionRepository.SumaPorCuenta;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaldoCuentaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private SaldoCuentaService service;

    @BeforeEach
    void preparar() {
        service = new SaldoCuentaService(
                transaccionRepository, cuentaRepository, presupuestoService);
    }

    @Test
    void sumaElSaldoInicialALasTransaccionesDeCadaCuenta() {
        Cuenta banco = cuenta(1L, 100_000L);
        Cuenta efectivo = cuenta(2L, 0L);
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(banco, efectivo));
        when(transaccionRepository.sumarPorCuenta(PRESUPUESTO_ID))
                .thenReturn(List.of(suma(1L, -25_000L, -5_000L), suma(2L, 700L, 0L)));

        List<SaldoCuentaResponse> saldos = service.listar(PRESUPUESTO_ID, USUARIO_ID);

        assertThat(saldos).containsExactly(
                new SaldoCuentaResponse(1L, 75_000L, 95_000L),
                new SaldoCuentaResponse(2L, 700L, 0L));
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void unaCuentaSinTransaccionesTieneSuSaldoInicial() {
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(cuenta(1L, 50_000L)));
        when(transaccionRepository.sumarPorCuenta(PRESUPUESTO_ID)).thenReturn(List.of());

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID))
                .containsExactly(new SaldoCuentaResponse(1L, 50_000L, 50_000L));
    }

    @Test
    void incluyeLasCuentasCerradas() {
        Cuenta cerrada = cuenta(1L, 10L);
        cerrada.cerrar();
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(cerrada));
        when(transaccionRepository.sumarPorCuenta(PRESUPUESTO_ID))
                .thenReturn(List.of(suma(1L, -4L, -4L)));

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID))
                .containsExactly(new SaldoCuentaResponse(1L, 6L, 6L));
    }

    @Test
    void sinCuentasDevuelveUnaListaVacia() {
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of());
        when(transaccionRepository.sumarPorCuenta(PRESUPUESTO_ID)).thenReturn(List.of());

        assertThat(service.listar(PRESUPUESTO_ID, USUARIO_ID)).isEmpty();
    }

    @Test
    void conPresupuestoAjenoDa404() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.listar(PRESUPUESTO_ID, USUARIO_ID));
    }

    private static Cuenta cuenta(long id, long saldoInicial) {
        return Cuenta.builder().id(id).saldoInicial(saldoInicial).build();
    }

    private static SumaPorCuenta suma(long cuentaId, long total, long conciliado) {
        return new SumaPorCuenta() {
            @Override
            public Long getCuentaId() {
                return cuentaId;
            }

            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getConciliado() {
                return conciliado;
            }
        };
    }
}
