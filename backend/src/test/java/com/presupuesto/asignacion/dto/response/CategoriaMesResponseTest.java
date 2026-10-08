package com.presupuesto.asignacion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.cuenta.entity.Cuenta;
import org.junit.jupiter.api.Test;

class CategoriaMesResponseTest {

    private static Categoria comida() {
        return Categoria.builder().id(7L).nombre("Comida").build();
    }

    @Test
    void mapeaTodosLosCampos() {
        CategoriaMesResponse respuesta =
                CategoriaMesResponse.desde(comida(), 100_000L, -30_000L, 70_000L);

        assertThat(respuesta).isEqualTo(
                new CategoriaMesResponse(7L, "Comida", false, 100_000L, -30_000L, 70_000L, false));
    }

    @Test
    void sobregastadaSoloConDisponibleNegativo() {
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, -1L).sobregastada()).isTrue();
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, 0L).sobregastada()).isFalse();
        assertThat(CategoriaMesResponse.desde(comida(), 0L, 0L, 1L).sobregastada()).isFalse();
    }

    @Test
    void reflejaLaCategoriaOculta() {
        Categoria oculta = comida();
        oculta.ocultar();

        assertThat(CategoriaMesResponse.desde(oculta, 0L, 0L, 0L).oculta()).isTrue();
    }

    @Test
    void unaCategoriaNormalNoEsDePagoYNoTieneCuenta() {
        CategoriaMesResponse respuesta = CategoriaMesResponse.desde(comida(), 0L, 0L, 0L);

        assertThat(respuesta.esPagoTarjeta()).isFalse();
        assertThat(respuesta.cuentaId()).isNull();
    }

    @Test
    void laCategoriaDePagoTraeLaMarcaYElIdDeLaTarjeta() {
        Categoria pago = Categoria.builder().id(8L).nombre("Pago: Visa")
                .cuentaTarjeta(Cuenta.builder().id(44L).build()).build();

        CategoriaMesResponse respuesta = CategoriaMesResponse.desde(pago, 0L, 30_000L, 30_000L);

        assertThat(respuesta.esPagoTarjeta()).isTrue();
        assertThat(respuesta.cuentaId()).isEqualTo(44L);
        assertThat(respuesta.disponible()).isEqualTo(30_000L);
    }

    @Test
    void elConstructorDeSieteCamposDejaLosCamposDeTarjetaEnSuValorNeutro() {
        CategoriaMesResponse respuesta =
                new CategoriaMesResponse(7L, "Comida", false, 1L, 2L, 3L, false);

        assertThat(respuesta.esPagoTarjeta()).isFalse();
        assertThat(respuesta.cuentaId()).isNull();
    }
}
