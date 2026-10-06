package com.presupuesto.asignacion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.Categoria;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AsignacionMensualTest {

    private static AsignacionMensual nueva(long asignado) {
        return AsignacionMensual.builder()
                .categoria(Categoria.builder().build())
                .mes(LocalDate.of(2026, 1, 1))
                .asignado(asignado)
                .build();
    }

    @Test
    void fijarReemplazaElAsignadoYAdmiteCeroYNegativos() {
        AsignacionMensual asignacion = nueva(100_000L);

        asignacion.fijarAsignado(60_000L);
        assertThat(asignacion.getAsignado()).isEqualTo(60_000L);
        asignacion.fijarAsignado(0L);
        assertThat(asignacion.getAsignado()).isZero();
        asignacion.fijarAsignado(-5_000L);
        assertThat(asignacion.getAsignado()).isEqualTo(-5_000L);
    }

    @Test
    void sumarAcumulaConSigno() {
        AsignacionMensual asignacion = nueva(100_000L);

        asignacion.sumarAsignado(30_000L);
        asignacion.sumarAsignado(-50_000L);

        assertThat(asignacion.getAsignado()).isEqualTo(80_000L);
    }

    @Test
    void elMesDebeSerElPrimerDia() {
        nueva(1L).validarMes();

        AsignacionMensual diaQuince = AsignacionMensual.builder()
                .categoria(Categoria.builder().build())
                .mes(LocalDate.of(2026, 1, 15))
                .build();

        assertThrows(IllegalStateException.class, diaQuince::validarMes);
        assertThrows(IllegalStateException.class,
                () -> AsignacionMensual.builder().build().validarMes());
    }

    @Test
    void noExponeSetters() {
        assertThat(Arrays.stream(AsignacionMensual.class.getDeclaredMethods())
                        .map(Method::getName)
                        .filter(nombre -> nombre.startsWith("set")))
                .isEmpty();
    }
}
