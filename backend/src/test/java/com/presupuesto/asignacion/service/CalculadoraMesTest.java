package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.repository.ActividadMensualRepository;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository.AsignadoPorMes;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import java.time.LocalDate;
import java.time.YearMonth;
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
class CalculadoraMesTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long COMIDA = 1L;
    private static final long OCIO = 2L;
    private static final YearMonth ENERO = YearMonth.of(2026, 1);
    private static final LocalDate FIN_ENERO = LocalDate.of(2026, 1, 31);

    @Mock
    private AsignacionMensualRepository asignacionRepository;

    @Mock
    private ActividadMensualRepository actividadRepository;

    private CalculadoraMes calculadora;

    @BeforeEach
    void preparar() {
        calculadora = new CalculadoraMes(asignacionRepository, actividadRepository);
        when(asignacionRepository.asignadosHasta(PRESUPUESTO_ID, LocalDate.of(2026, 1, 1)))
                .thenReturn(List.of(asignado(COMIDA, LocalDate.of(2026, 1, 1), 100_000L)));
        when(actividadRepository.actividadSimple(PRESUPUESTO_ID, FIN_ENERO))
                .thenReturn(List.of(actividad(COMIDA, 2026, 1, -20_000L)));
        when(actividadRepository.actividadDividida(PRESUPUESTO_ID, FIN_ENERO))
                .thenReturn(List.of(
                        actividad(COMIDA, 2026, 1, -10_000L), actividad(OCIO, 2026, 1, -5_000L)));
        when(actividadRepository.ingresosSinCategoria(PRESUPUESTO_ID, FIN_ENERO))
                .thenReturn(200_000L);
        when(actividadRepository.saldosInicialesPositivos(PRESUPUESTO_ID)).thenReturn(100_000L);
    }

    @Test
    void sumaLaActividadSimpleYDivididaDeLaMismaCategoriaYMes() {
        ResultadoMes resultado = calculadora.calcular(PRESUPUESTO_ID, ENERO);

        assertThat(resultado.fila(COMIDA)).isEqualTo(new FilaMes(100_000L, -30_000L, 70_000L));
        assertThat(resultado.fila(OCIO)).isEqualTo(new FilaMes(0L, -5_000L, -5_000L));
    }

    @Test
    void losIngresosSumanEntradasSinCategoriaYSaldosIniciales() {
        ResultadoMes resultado = calculadora.calcular(PRESUPUESTO_ID, ENERO);

        assertThat(resultado.listoParaAsignar()).isEqualTo(200_000L + 100_000L - 100_000L);
    }

    @Test
    void consultaLosRepositoriosConElFinDelMesPedido() {
        calculadora.calcular(PRESUPUESTO_ID, ENERO);

        verify(asignacionRepository).asignadosHasta(PRESUPUESTO_ID, LocalDate.of(2026, 1, 1));
        verify(actividadRepository).actividadSimple(PRESUPUESTO_ID, FIN_ENERO);
        verify(actividadRepository).actividadDividida(PRESUPUESTO_ID, FIN_ENERO);
        verify(actividadRepository).ingresosSinCategoria(PRESUPUESTO_ID, FIN_ENERO);
        verify(actividadRepository).saldosInicialesPositivos(PRESUPUESTO_ID);
    }

    @Test
    void unMesBisiestoUsaElDia29ComoTope() {
        YearMonth febrero = YearMonth.of(2028, 2);
        when(actividadRepository.ingresosSinCategoria(PRESUPUESTO_ID, LocalDate.of(2028, 2, 29)))
                .thenReturn(7L);

        ResultadoMes resultado = calculadora.calcular(PRESUPUESTO_ID, febrero);

        assertThat(resultado.listoParaAsignar()).isEqualTo(100_007L);
        verify(actividadRepository)
                .actividadSimple(PRESUPUESTO_ID, LocalDate.of(2028, 2, 29));
    }

    private static AsignadoPorMes asignado(long categoriaId, LocalDate mes, long asignado) {
        return new AsignadoPorMes() {
            @Override
            public Long getCategoriaId() {
                return categoriaId;
            }

            @Override
            public LocalDate getMes() {
                return mes;
            }

            @Override
            public Long getAsignado() {
                return asignado;
            }
        };
    }

    private static ActividadPorMes actividad(long categoriaId, int anio, int mes, long total) {
        return new ActividadPorMes() {
            @Override
            public Long getCategoriaId() {
                return categoriaId;
            }

            @Override
            public Integer getAnio() {
                return anio;
            }

            @Override
            public Integer getMes() {
                return mes;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }
}
