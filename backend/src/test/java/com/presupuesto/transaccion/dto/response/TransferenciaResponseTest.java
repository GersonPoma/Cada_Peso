package com.presupuesto.transaccion.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.transaccion.entity.Transaccion;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransferenciaResponseTest {

    private static Transaccion pata(long id, long cuentaId, long monto) {
        return Transaccion.builder()
                .id(id).cuenta(Cuenta.builder().id(cuentaId).build())
                .fecha(LocalDate.of(2026, 9, 1)).monto(monto).build();
    }

    @Test
    void mapeaLaSalidaYLaEntradaConSuPar() {
        Transaccion salida = pata(1L, 10L, -30000L);
        Transaccion entrada = pata(2L, 11L, 30000L);
        salida.enlazarCon(entrada);
        entrada.enlazarCon(salida);

        TransferenciaResponse respuesta = TransferenciaResponse.desde(salida, entrada);

        assertThat(respuesta.salida().id()).isEqualTo(1L);
        assertThat(respuesta.salida().monto()).isEqualTo(-30000L);
        assertThat(respuesta.salida().transaccionParId()).isEqualTo(2L);
        assertThat(respuesta.entrada().id()).isEqualTo(2L);
        assertThat(respuesta.entrada().cuentaId()).isEqualTo(11L);
        assertThat(respuesta.entrada().transaccionParId()).isEqualTo(1L);
    }
}
