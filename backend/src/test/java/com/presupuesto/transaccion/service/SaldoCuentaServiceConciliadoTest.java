package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaldoCuentaServiceConciliadoTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 10, 10);

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
    void sumaElSaldoInicialALoConciliadoHastaElCorte() {
        Cuenta cuenta = Cuenta.builder().id(1L).saldoInicial(500_000L).build();
        when(transaccionRepository.sumaConciliadaDeCuenta(1L, CORTE)).thenReturn(-200_000L);

        assertThat(service.saldoConciliadoAl(cuenta, CORTE)).isEqualTo(300_000L);
    }

    @Test
    void sinLimiteIncluyeLasPosteriores() {
        Cuenta cuenta = Cuenta.builder().id(1L).saldoInicial(500_000L).build();
        when(transaccionRepository.sumaConciliadaDeCuenta(1L, SaldoCuentaService.SIN_LIMITE))
                .thenReturn(-250_000L);

        assertThat(service.saldoConciliadoAl(cuenta, SaldoCuentaService.SIN_LIMITE))
                .isEqualTo(250_000L);
    }
}
