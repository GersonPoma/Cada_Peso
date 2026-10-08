package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.EvolucionSaldoResponse;
import com.presupuesto.reporte.dto.response.PatrimonioResponse;
import com.presupuesto.reporte.repository.MovimientoMensualRepository;
import com.presupuesto.reporte.repository.MovimientoMensualRepository.MovimientoPorMes;
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
class PatrimonioReporteServiceTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long USUARIO_ID = 5L;
    private static final long CUENTA_ID = 31L;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private MovimientoMensualRepository movimientoRepository;

    private PatrimonioReporteService service;

    @BeforeEach
    void preparar() {
        service = new PatrimonioReporteService(
                presupuestoService, cuentaRepository, movimientoRepository,
                new ReporteProperties(60));
    }

    @Test
    void conUnPresupuestoAjenoResponde404SinBuscarLaCuentaNiValidarElRango() {
        doThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"))
                .when(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);

        assertThatThrownBy(() -> service.evolucionSaldo(
                PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, "2026-13", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.patrimonio(
                PRESUPUESTO_ID, USUARIO_ID, "2026-13", null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(cuentaRepository, movimientoRepository);
    }

    @Test
    void conUnaCuentaAjenaResponde404AntesQueElRangoInvalido() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evolucionSaldo(
                PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, "2026-13", "2026-12"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(movimientoRepository);
    }

    @Test
    void unRangoInvalidoNoConsultaMovimientos() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta(CUENTA_ID, TipoCuenta.CORRIENTE, 0L)));

        assertThatThrownBy(() -> service.evolucionSaldo(
                PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, "2026-10", "2026-09"))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> service.patrimonio(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-09"))
                .isInstanceOf(DatosInvalidosException.class);

        verifyNoInteractions(movimientoRepository);
    }

    @Test
    void elPatrimonioUsaUnaSolaConsultaDeMovimientosYDeCuentasSinImportarElRango() {
        when(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID))
                .thenReturn(List.of(
                        cuenta(1L, TipoCuenta.CORRIENTE, 1_000_000L),
                        cuenta(2L, TipoCuenta.TARJETA_CREDITO, -200_000L)));
        List<MovimientoPorMes> movimientos = List.of(
                movimiento(1L, 2026, 9, 200_000L, 0L), movimiento(2L, 2026, 9, 50_000L, 0L));
        when(movimientoRepository.movimientoPorCuentaYMes(eq(PRESUPUESTO_ID), any()))
                .thenReturn(movimientos);

        PatrimonioResponse corto =
                service.patrimonio(PRESUPUESTO_ID, USUARIO_ID, "2026-09", "2026-09");
        PatrimonioResponse largo =
                service.patrimonio(PRESUPUESTO_ID, USUARIO_ID, "2024-10", "2026-09");

        assertThat(corto.meses()).hasSize(1);
        assertThat(corto.meses().get(0).activos()).isEqualTo(1_200_000L);
        assertThat(corto.meses().get(0).pasivos()).isEqualTo(150_000L);
        assertThat(corto.meses().get(0).patrimonio()).isEqualTo(1_050_000L);
        assertThat(largo.meses()).hasSize(24);
        assertThat(largo.meses().get(0).patrimonio()).isEqualTo(800_000L);
        verify(movimientoRepository, times(2))
                .movimientoPorCuentaYMes(eq(PRESUPUESTO_ID), eq(LocalDate.of(2026, 9, 30)));
        verify(cuentaRepository, times(2))
                .findByPresupuestoIdOrderByNombreNormalizado(PRESUPUESTO_ID);
    }

    @Test
    void laEvolucionTraeLosDatosDeLaCuentaYElSaldoDeAperturaDelRango() {
        Cuenta cuenta = cuenta(CUENTA_ID, TipoCuenta.CORRIENTE, 100_000L);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cuenta));
        List<MovimientoPorMes> movimientos = List.of(
                movimiento(CUENTA_ID, 2025, 11, 40_000L, 0L),
                movimiento(CUENTA_ID, 2025, 12, 0L, -10_000L),
                movimiento(CUENTA_ID, 2026, 1, 5_000L, -2_000L));
        when(movimientoRepository.movimientoDeCuentaPorMes(eq(CUENTA_ID), any()))
                .thenReturn(movimientos);

        EvolucionSaldoResponse respuesta = service.evolucionSaldo(
                PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, "2026-01", "2026-02");

        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(respuesta.saldoInicial()).isEqualTo(100_000L);
        assertThat(respuesta.meses()).hasSize(2);
        assertThat(respuesta.meses().get(0).saldo()).isEqualTo(133_000L);
        assertThat(respuesta.meses().get(0).entradas()).isEqualTo(5_000L);
        assertThat(respuesta.meses().get(0).salidas()).isEqualTo(-2_000L);
        assertThat(respuesta.meses().get(1).saldo()).isEqualTo(133_000L);
        verify(movimientoRepository)
                .movimientoDeCuentaPorMes(CUENTA_ID, LocalDate.of(2026, 2, 28));
    }

    private static Cuenta cuenta(long id, TipoCuenta tipo, long saldoInicial) {
        Cuenta cuenta = Cuenta.builder()
                .nombre("Cuenta " + id).tipo(tipo).saldoInicial(saldoInicial).build();
        cuenta.setId(id);
        return cuenta;
    }

    private static MovimientoPorMes movimiento(
            long cuentaId, int anio, int mes, long entradas, long salidas) {
        MovimientoPorMes fila = mock(MovimientoPorMes.class);
        when(fila.getCuentaId()).thenReturn(cuentaId);
        when(fila.getAnio()).thenReturn(anio);
        when(fila.getMes()).thenReturn(mes);
        when(fila.getEntradas()).thenReturn(entradas);
        when(fila.getSalidas()).thenReturn(salidas);
        return fila;
    }
}
