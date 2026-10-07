package com.presupuesto.transaccion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransaccionResponseTest {

    private static final Instant CREADA = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant ACTUALIZADA = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void mapeaTodosLosCamposDeUnaTransaccionSimple() {
        Categoria comida = Categoria.builder().id(7L).build();
        Transaccion transaccion = Transaccion.builder()
                .id(1L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 10, 2))
                .monto(-2500L)
                .categoria(comida)
                .beneficiario("Tienda")
                .memo("nota")
                .estado(EstadoTransaccion.CONCILIADA)
                .aprobada(false)
                .fechaCreacion(CREADA)
                .fechaActualizacion(ACTUALIZADA)
                .build();

        TransaccionResponse respuesta = TransaccionResponse.desde(transaccion);

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.cuentaId()).isEqualTo(3L);
        assertThat(respuesta.fecha()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(respuesta.monto()).isEqualTo(-2500L);
        assertThat(respuesta.categoriaId()).isEqualTo(7L);
        assertThat(respuesta.beneficiario()).isEqualTo("Tienda");
        assertThat(respuesta.beneficiarioId()).isNull();
        assertThat(respuesta.memo()).isEqualTo("nota");
        assertThat(respuesta.estado()).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(respuesta.aprobada()).isFalse();
        assertThat(respuesta.subtransacciones()).isEmpty();
        assertThat(respuesta.transaccionParId()).isNull();
        assertThat(respuesta.fechaCreacion()).isEqualTo(CREADA);
        assertThat(respuesta.fechaActualizacion()).isEqualTo(ACTUALIZADA);
    }

    @Test
    void conBeneficiarioVinculadoExponeSuId() {
        Transaccion transaccion = Transaccion.builder()
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 10, 2))
                .monto(-2500L)
                .beneficiario("Netflix")
                .beneficiarioVinculado(Beneficiario.builder().id(12L).nombre("Netflix").build())
                .build();

        TransaccionResponse respuesta = TransaccionResponse.desde(transaccion);

        assertThat(respuesta.beneficiario()).isEqualTo("Netflix");
        assertThat(respuesta.beneficiarioId()).isEqualTo(12L);
    }

    @Test
    void sinCategoriaDaCategoriaIdNuloYMapeaLasSubtransacciones() {
        Transaccion transaccion = Transaccion.builder()
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 10, 2))
                .monto(-3000L)
                .build();
        transaccion.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().id(10L).monto(-1000L).memo("a").build(),
                SubTransaccion.builder()
                        .id(11L).monto(-2000L).categoria(Categoria.builder().id(8L).build())
                        .build()));

        TransaccionResponse respuesta = TransaccionResponse.desde(transaccion);

        assertThat(respuesta.categoriaId()).isNull();
        assertThat(respuesta.estado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(respuesta.aprobada()).isTrue();
        assertThat(respuesta.subtransacciones())
                .containsExactly(
                        new SubTransaccionResponse(10L, null, -1000L, "a"),
                        new SubTransaccionResponse(11L, 8L, -2000L, null));
    }

    @Test
    void unaPataDeTransferenciaExponeElIdDeSuPar() {
        Transaccion entrada = Transaccion.builder()
                .id(21L)
                .cuenta(Cuenta.builder().id(4L).build())
                .fecha(LocalDate.of(2026, 10, 2))
                .monto(3000L)
                .build();
        Transaccion salida = Transaccion.builder()
                .id(20L)
                .cuenta(Cuenta.builder().id(3L).build())
                .fecha(LocalDate.of(2026, 10, 2))
                .monto(-3000L)
                .build();
        salida.enlazarCon(entrada);

        assertThat(TransaccionResponse.desde(salida).transaccionParId()).isEqualTo(21L);
    }
}
