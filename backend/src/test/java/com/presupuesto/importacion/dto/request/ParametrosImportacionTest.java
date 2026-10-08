package com.presupuesto.importacion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParametrosImportacionTest {

    private static Map<String, String> validos() {
        Map<String, String> p = new HashMap<>();
        p.put("separador", "PUNTO_Y_COMA");
        p.put("columnaFecha", "0");
        p.put("formatoFecha", "dd/MM/yyyy");
        p.put("columnaMonto", "2");
        p.put("separadorDecimal", "COMA");
        p.put("columnaDescripcion", "1");
        return p;
    }

    private static void esInvalido(Map<String, String> parametros) {
        assertThrows(DatosInvalidosException.class, () -> ParametrosImportacion.de(parametros));
    }

    @Test
    void leeElMapeoCompletoConLosValoresPorDefecto() {
        ParametrosImportacion p = ParametrosImportacion.de(validos());

        assertThat(p.separador()).isEqualTo(Separador.PUNTO_Y_COMA);
        assertThat(p.tieneEncabezado()).isFalse();
        assertThat(p.columnaFecha()).isZero();
        assertThat(p.formatoFecha()).isEqualTo(FormatoFecha.DIA_MES_ANIO);
        assertThat(p.montoEnUnaColumna()).isTrue();
        assertThat(p.columnaMonto()).isEqualTo(2);
        assertThat(p.separadorDecimal()).isEqualTo(SeparadorDecimal.COMA);
        assertThat(p.separadorMiles()).isEqualTo(SeparadorMiles.NINGUNO);
        assertThat(p.columnaDescripcion()).isEqualTo(1);
        assertThat(p.columnaMemo()).isNull();
        assertThat(p.omitirInvalidas()).isFalse();
    }

    @Test
    void aceptaDebitoYCreditoMemoMilesYBooleanos() {
        Map<String, String> m = validos();
        m.remove("columnaMonto");
        m.put("columnaDebito", "2");
        m.put("columnaCredito", "3");
        m.put("columnaMemo", " 4 ");
        m.put("separadorMiles", "PUNTO");
        m.put("tieneEncabezado", "TRUE");
        m.put("omitirInvalidas", "true");

        ParametrosImportacion p = ParametrosImportacion.de(m);

        assertThat(p.montoEnUnaColumna()).isFalse();
        assertThat(p.columnaDebito()).isEqualTo(2);
        assertThat(p.columnaCredito()).isEqualTo(3);
        assertThat(p.columnaMemo()).isEqualTo(4);
        assertThat(p.separadorMiles()).isEqualTo(SeparadorMiles.PUNTO);
        assertThat(p.tieneEncabezado()).isTrue();
        assertThat(p.omitirInvalidas()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "separador", "columnaFecha", "formatoFecha", "separadorDecimal", "columnaDescripcion"})
    void faltarUnObligatorioEsUn400(String nombre) {
        Map<String, String> m = validos();
        m.remove(nombre);

        esInvalido(m);
    }

    @Test
    void faltarTodoElMontoEsUn400() {
        Map<String, String> m = validos();
        m.remove("columnaMonto");

        esInvalido(m);
    }

    @Test
    void soloDebitoOSoloCreditoEsUn400() {
        Map<String, String> soloDebito = validos();
        soloDebito.remove("columnaMonto");
        soloDebito.put("columnaDebito", "2");
        esInvalido(soloDebito);

        Map<String, String> soloCredito = validos();
        soloCredito.remove("columnaMonto");
        soloCredito.put("columnaCredito", "2");
        esInvalido(soloCredito);
    }

    @Test
    void montoEnUnaYEnDosColumnasEsUn400() {
        Map<String, String> m = validos();
        m.put("columnaDebito", "3");
        m.put("columnaCredito", "4");
        esInvalido(m);

        Map<String, String> mezcla = validos();
        mezcla.put("columnaDebito", "3");
        esInvalido(mezcla);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "formatoFecha|yy/MM/dd",
        "formatoFecha|dd/mm/yyyy",
        "formatoFecha|DIA_MES_ANIO",
        "separador|PUNTOYCOMA",
        "separador|coma",
        "separador|;",
        "separadorDecimal|PUNTO_Y_COMA",
        "separadorDecimal|ninguno",
        "separadorMiles|APOSTROFO",
        "columnaFecha|-1",
        "columnaFecha|uno",
        "columnaFecha|1.5",
        "columnaMonto|-3",
        "columnaDescripcion|9999999999",
        "columnaMemo|x",
        "tieneEncabezado|quizas",
        "tieneEncabezado|1",
        "omitirInvalidas|quizas",
        "omitirInvalidas|si"})
    void unValorFueraDeLaListaOMalFormadoEsUn400(String nombre, String valor) {
        Map<String, String> m = validos();
        m.put(nombre, valor);

        esInvalido(m);
    }

    @Test
    void separadorDeMilesIgualAlDecimalEsUn400() {
        Map<String, String> comas = validos();
        comas.put("separadorMiles", "COMA");
        esInvalido(comas);

        Map<String, String> puntos = validos();
        puntos.put("separadorDecimal", "PUNTO");
        puntos.put("separadorMiles", "PUNTO");
        esInvalido(puntos);
    }

    @Test
    void milesDistintoDelDecimalEsValido() {
        Map<String, String> m = validos();
        m.put("separadorMiles", "ESPACIO");

        assertThat(ParametrosImportacion.de(m).separadorMiles()).isEqualTo(SeparadorMiles.ESPACIO);
    }

    @Test
    void cadaFormatoDeFechaDeLaListaCerradaSeAcepta() {
        for (String formato : new String[] {
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy"}) {
            Map<String, String> m = validos();
            m.put("formatoFecha", formato);

            assertThat(ParametrosImportacion.de(m).formatoFecha()).isNotNull();
        }
    }

    @Test
    void elMensajeNoRepiteElValorRecibido() {
        Map<String, String> m = validos();
        m.put("formatoFecha", "<script>");

        DatosInvalidosException e =
                assertThrows(DatosInvalidosException.class, () -> ParametrosImportacion.de(m));

        assertThat(e.getMessage()).doesNotContain("script");
    }
}
