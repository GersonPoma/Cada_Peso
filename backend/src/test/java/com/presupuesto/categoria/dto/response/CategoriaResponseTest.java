package com.presupuesto.categoria.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.cuenta.entity.Cuenta;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CategoriaResponseTest {

    @Test
    void desdeCopiaTodosLosCamposDeLaEntidad() {
        Instant creada = Instant.parse("2026-01-01T10:00:00Z");
        Instant actualizada = Instant.parse("2026-01-02T10:00:00Z");
        Categoria categoria = Categoria.builder()
                .id(8L)
                .grupo(GrupoCategoria.builder().id(3L).build())
                .nombre("Alquiler")
                .orden(1)
                .nota("Mensual")
                .fechaCreacion(creada)
                .fechaActualizacion(actualizada)
                .build();
        categoria.ocultar();

        CategoriaResponse response = CategoriaResponse.desde(categoria);

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.grupoId()).isEqualTo(3L);
        assertThat(response.nombre()).isEqualTo("Alquiler");
        assertThat(response.orden()).isEqualTo(1);
        assertThat(response.oculta()).isTrue();
        assertThat(response.nota()).isEqualTo("Mensual");
        assertThat(response.fechaCreacion()).isEqualTo(creada);
        assertThat(response.fechaActualizacion()).isEqualTo(actualizada);
    }

    @Test
    void unaCategoriaNormalNoEsDePagoYNoTieneCuenta() {
        Categoria categoria = Categoria.builder()
                .id(8L).grupo(GrupoCategoria.builder().id(3L).build()).nombre("Alquiler").build();

        CategoriaResponse response = CategoriaResponse.desde(categoria);

        assertThat(response.esPagoTarjeta()).isFalse();
        assertThat(response.cuentaId()).isNull();
    }

    @Test
    void laCategoriaDePagoTraeLaMarcaYElIdDeLaCuenta() {
        Categoria categoria = Categoria.builder()
                .id(9L)
                .grupo(GrupoCategoria.builder().id(3L).build())
                .nombre("Pago: Visa")
                .cuentaTarjeta(Cuenta.builder().id(44L).build())
                .build();

        CategoriaResponse response = CategoriaResponse.desde(categoria);

        assertThat(response.esPagoTarjeta()).isTrue();
        assertThat(response.cuentaId()).isEqualTo(44L);
    }
}
