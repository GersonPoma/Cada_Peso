package com.presupuesto.importacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.transaccion.service.ClaveMovimiento;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClasificadorDuplicadosTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 5);
    private static final ClaveMovimiento CAFE = ClaveMovimiento.de(FECHA, -4500, "Cafe Luna");
    private static final ClaveMovimiento PAN = ClaveMovimiento.de(FECHA, -1200, "Panaderia");

    private static List<ClaveMovimiento> filas(ClaveMovimiento clave, int veces) {
        return Collections.nCopies(veces, clave);
    }

    /** E = transacciones de la cuenta, F = filas del archivo, N = nuevas esperadas. */
    @ParameterizedTest(name = "cuenta {0}, archivo {1} -> {2} duplicadas y {3} nuevas")
    @CsvSource({
        "0, 1, 0, 1",
        "0, 2, 0, 2",
        "1, 2, 1, 1",
        "2, 2, 2, 0",
        "2, 1, 1, 0",
        "0, 3, 0, 3",
        "3, 3, 3, 0",
        "1, 1, 1, 0"})
    void cuentaOcurrencias(int existentes, int enArchivo, int duplicadas, int nuevas) {
        List<Boolean> resultado = ClasificadorDuplicados.clasificar(
                filas(CAFE, enArchivo), Map.of(CAFE, existentes));

        assertThat(resultado.stream().filter(d -> d)).hasSize(duplicadas);
        assertThat(resultado.stream().filter(d -> !d)).hasSize(nuevas);
    }

    @Test
    void lasPrimerasEnOrdenDeArchivoSonLasDuplicadas() {
        assertThat(ClasificadorDuplicados.clasificar(filas(CAFE, 3), Map.of(CAFE, 2)))
                .containsExactly(true, true, false);
    }

    @Test
    void clavesDistintasIntercaladasSeCuentanPorSeparado() {
        List<ClaveMovimiento> archivo = new ArrayList<>(
                List.of(CAFE, PAN, CAFE, PAN, PAN, CAFE));

        List<Boolean> resultado = ClasificadorDuplicados.clasificar(
                archivo, Map.of(CAFE, 1, PAN, 2));

        assertThat(resultado).containsExactly(true, true, false, true, false, false);
    }

    @Test
    void lasFilasInvalidasNoParticipanNiConsumenOcurrencias() {
        List<ClaveMovimiento> archivo = new ArrayList<>();
        archivo.add(null);
        archivo.add(CAFE);
        archivo.add(null);

        assertThat(ClasificadorDuplicados.clasificar(archivo, Map.of(CAFE, 1)))
                .containsExactly(false, true, false);
    }

    @Test
    void sinExistentesTodasSonNuevas() {
        assertThat(ClasificadorDuplicados.clasificar(filas(CAFE, 2), Map.of()))
                .containsExactly(false, false);
    }

    @Test
    void listaVaciaDaListaVacia() {
        assertThat(ClasificadorDuplicados.clasificar(List.of(), Map.of(CAFE, 3))).isEmpty();
    }

    @Test
    void laClaveDistingueFechaMontoYBeneficiario() {
        ClaveMovimiento otroDia = ClaveMovimiento.de(FECHA.plusDays(1), -4500, "Cafe Luna");
        ClaveMovimiento otroMonto = ClaveMovimiento.de(FECHA, -4501, "Cafe Luna");
        ClaveMovimiento otroBeneficiario = ClaveMovimiento.de(FECHA, -4500, "Cafe Sol");
        List<ClaveMovimiento> archivo = List.of(otroDia, otroMonto, otroBeneficiario);

        assertThat(ClasificadorDuplicados.clasificar(archivo, Map.of(CAFE, 5)))
                .containsExactly(false, false, false);
    }
}
