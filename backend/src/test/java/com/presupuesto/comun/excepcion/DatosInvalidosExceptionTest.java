package com.presupuesto.comun.excepcion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatosInvalidosExceptionTest {

    @Test
    void llevaElCodigoDatosInvalidosYElMensaje() {
        DatosInvalidosException excepcion = new DatosInvalidosException("Posicion fuera de rango");

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        assertThat(excepcion.getMessage()).isEqualTo("Posicion fuera de rango");
        assertThat(excepcion).isInstanceOf(NegocioException.class);
    }
}
