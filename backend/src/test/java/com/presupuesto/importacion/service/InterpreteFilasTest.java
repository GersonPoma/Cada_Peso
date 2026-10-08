package com.presupuesto.importacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.importacion.dto.request.FormatoFecha;
import com.presupuesto.importacion.dto.request.ParametrosImportacion;
import com.presupuesto.importacion.dto.request.Separador;
import com.presupuesto.importacion.dto.request.SeparadorDecimal;
import com.presupuesto.importacion.dto.request.SeparadorMiles;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterpreteFilasTest {

    private static ParametrosImportacion unaColumna(
            FormatoFecha formato, SeparadorDecimal decimal, SeparadorMiles miles) {
        return new ParametrosImportacion(
                Separador.PUNTO_Y_COMA, false, 0, formato, 1, null, null, decimal, miles, 2, 3,
                false);
    }

    private static ParametrosImportacion dosColumnas() {
        return new ParametrosImportacion(
                Separador.COMA, false, 0, FormatoFecha.ISO, null, 1, 2,
                SeparadorDecimal.COMA, SeparadorMiles.PUNTO, 3, null, false);
    }

    private static long monto(String texto, SeparadorDecimal decimal, SeparadorMiles miles) {
        FilaInterpretada fila = InterpreteFilas.interpretar(
                1, List.of("2026-03-05", texto, "x", ""),
                unaColumna(FormatoFecha.ISO, decimal, miles));
        assertThat(fila.motivo()).as("motivo de %s", texto).isNull();
        return fila.monto();
    }

    private static FilaInterpretada interpretar(
            ParametrosImportacion p, String... campos) {
        return InterpreteFilas.interpretar(7, List.of(campos), p);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "-1.234,5|COMA|PUNTO|-1234500",
        "4,50|COMA|PUNTO|4500",
        "1.234|COMA|PUNTO|1234000",
        "1.234|PUNTO|NINGUNO|1234",
        "-4.5|PUNTO|NINGUNO|-4500",
        "+10|PUNTO|NINGUNO|10000",
        "0.001|PUNTO|NINGUNO|1",
        "1,234.56|PUNTO|COMA|1234560",
        ".5|PUNTO|NINGUNO|500",
        "5.|PUNTO|NINGUNO|5000",
        "1 234,50|COMA|ESPACIO|1234500",
        "1005|PUNTO|NINGUNO|1005000",
        "9223372036854775|PUNTO|NINGUNO|9223372036854775000"})
    void leeElMontoAMilesimasSinFlotantes(
            String texto, SeparadorDecimal decimal, SeparadorMiles miles, long esperado) {
        assertThat(monto(texto, decimal, miles)).isEqualTo(esperado);
    }

    @Test
    void elEspacioNoSeparableEsSeparadorDeMilesConEspacio() {
        assertThat(monto("1 234,50", SeparadorDecimal.COMA, SeparadorMiles.ESPACIO))
                .isEqualTo(1_234_500L);
        assertThat(monto("1 234,50", SeparadorDecimal.COMA, SeparadorMiles.ESPACIO))
                .isEqualTo(1_234_500L);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "10.0005|PUNTO|NINGUNO",
        "abc|PUNTO|NINGUNO",
        "|PUNTO|NINGUNO",
        "-|PUNTO|NINGUNO",
        ".|PUNTO|NINGUNO",
        "Bs 10|PUNTO|NINGUNO",
        "(10.00)|PUNTO|NINGUNO",
        "1.2.3|PUNTO|NINGUNO",
        "1,5|PUNTO|NINGUNO",
        "1 000.00|PUNTO|NINGUNO",
        "1e3|PUNTO|NINGUNO",
        "--5|PUNTO|NINGUNO",
        "١٢٣|PUNTO|NINGUNO"})
    void rechazaMontosMalFormados(String texto, SeparadorDecimal decimal, SeparadorMiles miles) {
        String celda = texto == null ? "" : texto;
        FilaInterpretada fila = InterpreteFilas.interpretar(
                1, List.of("2026-03-05", celda, "x", ""),
                unaColumna(FormatoFecha.ISO, decimal, miles));

        assertThat(fila.valida()).isFalse();
        assertThat(fila.motivo()).isEqualTo("El monto no es válido");
    }

    @Test
    void unMontoQueNoCabeEnUnLongEsInvalidoSinLanzar() {
        for (String texto : List.of("9223372036854776", "99999999999999999999")) {
            FilaInterpretada fila = interpretar(
                    unaColumna(FormatoFecha.ISO, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                    "2026-03-05", texto, "x", "");

            assertThat(fila.valida()).isFalse();
            assertThat(fila.motivo()).isEqualTo("El monto está fuera de rango");
        }
    }

    @Test
    void montoCeroEsInvalido() {
        FilaInterpretada fila = interpretar(
                unaColumna(FormatoFecha.ISO, SeparadorDecimal.COMA, SeparadorMiles.PUNTO),
                "2026-03-05", "0,00", "x", "");

        assertThat(fila.motivo()).isEqualTo("El monto no puede ser 0");
    }

    @Test
    void debitoYCreditoSeRestan() {
        assertThat(interpretar(dosColumnas(), "2026-03-05", "45,00", "", "Super").monto())
                .isEqualTo(-45_000L);
        assertThat(interpretar(dosColumnas(), "2026-03-05", "", "100,00", "Sueldo").monto())
                .isEqualTo(100_000L);
        assertThat(interpretar(dosColumnas(), "2026-03-05", "-45,00", "", "Super").monto())
                .isEqualTo(-45_000L);
    }

    @Test
    void debitoYCreditoALaVezOAmbosVaciosSonInvalidos() {
        assertThat(interpretar(dosColumnas(), "2026-03-05", "10,00", "5,00", "x").motivo())
                .isEqualTo("La fila tiene débito y crédito a la vez");
        assertThat(interpretar(dosColumnas(), "2026-03-05", "", "", "x").motivo())
                .isEqualTo("El monto no puede ser 0");
        assertThat(interpretar(dosColumnas(), "2026-03-05", "0,00", "0,00", "x").motivo())
                .isEqualTo("El monto no puede ser 0");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ISO|2026-03-05|2026-03-05",
        "DIA_MES_ANIO|05/03/2026|2026-03-05",
        "MES_DIA_ANIO|03/05/2026|2026-03-05",
        "DIA_MES_ANIO_GUION|05-03-2026|2026-03-05",
        "DIA_MES_ANIO|29/02/2028|2028-02-29"})
    void leeCadaFormatoDeFecha(FormatoFecha formato, String texto, LocalDate esperada) {
        FilaInterpretada fila = interpretar(
                unaColumna(formato, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                " " + texto + " ", "1", "x", "");

        assertThat(fila.fecha()).isEqualTo(esperada);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "DIA_MES_ANIO|31/02/2026",
        "DIA_MES_ANIO|29/02/2026",
        "MES_DIA_ANIO|25/03/2026",
        "DIA_MES_ANIO|5/3/2026",
        "DIA_MES_ANIO|05/03/26",
        "DIA_MES_ANIO|05/03/2026 10:00",
        "DIA_MES_ANIO|2026-03-05",
        "ISO|2026-3-5",
        "ISO|ayer",
        "ISO|"})
    void rechazaFechasInexistentesOQueNoCumplenElFormato(FormatoFecha formato, String texto) {
        FilaInterpretada fila = interpretar(
                unaColumna(formato, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                texto == null ? "" : texto, "1", "x", "");

        assertThat(fila.valida()).isFalse();
        assertThat(fila.motivo()).isEqualTo("La fecha no es válida");
    }

    @Test
    void siFaltaLaColumnaLaFilaEsInvalidaYElMotivoNoTraeElContenido() {
        FilaInterpretada fila = interpretar(
                unaColumna(FormatoFecha.ISO, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                "2026-03-05", "10", "secreto");

        assertThat(fila.motivo()).isEqualTo("Falta la columna con índice 3");
        assertThat(fila.fila()).isEqualTo(7);
    }

    @Test
    void elBeneficiarioSeRecortaYSeTruncaA100() {
        String largo = "ñ".repeat(150);
        FilaInterpretada fila = interpretar(
                unaColumna(FormatoFecha.ISO, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                "2026-03-05", "10", "  " + largo + "  ", "");

        assertThat(fila.beneficiario()).isEqualTo("ñ".repeat(100));
        assertThat(fila.valida()).isTrue();
    }

    @Test
    void elMemoSeTruncaA500() {
        FilaInterpretada fila = interpretar(
                unaColumna(FormatoFecha.ISO, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                "2026-03-05", "10", "x", "m".repeat(800));

        assertThat(fila.memo()).hasSize(500);
    }

    @Test
    void elTruncadoNoPartiUnParSustituto() {
        String emoji = "😀";
        String texto = "a".repeat(99) + emoji + "zzz";

        String truncado = InterpreteFilas.texto(texto, 100);

        assertThat(truncado).isEqualTo("a".repeat(99) + emoji);
        assertThat(truncado.codePointCount(0, truncado.length())).isEqualTo(100);
    }

    @Test
    void trasTruncarSeVuelveARecortar() {
        assertThat(InterpreteFilas.texto("ab   cd", 5)).isEqualTo("ab");
    }

    @Test
    void textoVacioOSoloEspaciosEsNulo() {
        assertThat(InterpreteFilas.texto("", 100)).isNull();
        assertThat(InterpreteFilas.texto("   \t ", 100)).isNull();
    }

    @Test
    void unaDescripcionLargaNoInvalidaLaFila() {
        FilaInterpretada fila = interpretar(
                unaColumna(FormatoFecha.ISO, SeparadorDecimal.PUNTO, SeparadorMiles.NINGUNO),
                "2026-03-05", "10", "x".repeat(1000), "");

        assertThat(fila.valida()).isTrue();
    }

    @Test
    void sinColumnaDeMemoElMemoEsNulo() {
        FilaInterpretada fila = interpretar(dosColumnas(), "2026-03-05", "", "5,00", "Sueldo");

        assertThat(fila.memo()).isNull();
        assertThat(fila.beneficiario()).isEqualTo("Sueldo");
    }
}
