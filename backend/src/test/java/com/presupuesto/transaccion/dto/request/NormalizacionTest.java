package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizacionTest {

    @Test
    void recortarONuloRecortaConservandoLoInterno() {
        assertThat(Normalizacion.recortarONulo("  Mi  tienda  ")).isEqualTo("Mi  tienda");
    }

    @Test
    void vacioSoloEspaciosONuloDanNulo() {
        assertThat(Normalizacion.recortarONulo("")).isNull();
        assertThat(Normalizacion.recortarONulo("   ")).isNull();
        assertThat(Normalizacion.recortarONulo(null)).isNull();
    }
}
