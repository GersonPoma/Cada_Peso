package com.presupuesto.categoria.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizacionTest {

    @Test
    void elNombreSeRecortaYElNuloSeConserva() {
        assertThat(Normalizacion.nombre("  Mi Grupo  ")).isEqualTo("Mi Grupo");
        assertThat(Normalizacion.nombre(null)).isNull();
    }

    @Test
    void laNotaAusenteVaciaOEnBlancoQuedaNula() {
        assertThat(Normalizacion.nota(null)).isNull();
        assertThat(Normalizacion.nota("")).isNull();
        assertThat(Normalizacion.nota("   ")).isNull();
    }

    @Test
    void laNotaConTextoSeRecorta() {
        assertThat(Normalizacion.nota("  Pago mensual  ")).isEqualTo("Pago mensual");
    }
}
