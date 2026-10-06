package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MesParametroTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "2026-13", "2026-1", "2026-00", "26-01", "enero", "1999-12", "2101-01", "2026-01-01",
        " 2026-01", "2026/01", "+026-01"
    })
    void unMesInvalidoDa400DatosInvalidos(String texto) {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                () -> MesParametro.interpretar(texto));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
    }

    @Test
    void losMesesValidosIncluidosLosExtremosSeInterpretan() {
        assertThat(MesParametro.interpretar("2026-01")).isEqualTo(YearMonth.of(2026, 1));
        assertThat(MesParametro.interpretar("2000-01")).isEqualTo(YearMonth.of(2000, 1));
        assertThat(MesParametro.interpretar("2100-12")).isEqualTo(YearMonth.of(2100, 12));
        assertThat(MesParametro.interpretar("2030-05")).isEqualTo(YearMonth.of(2030, 5));
    }
}
