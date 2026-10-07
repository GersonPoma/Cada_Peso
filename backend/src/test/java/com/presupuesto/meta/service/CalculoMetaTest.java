package com.presupuesto.meta.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.meta.dto.response.EstadoMeta;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.service.CalculoMeta.Resultado;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CalculoMetaTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    private static Meta mensual(long monto) {
        return meta(TipoMeta.MONTO_MENSUAL, monto, FrecuenciaMeta.MENSUAL, null, null, null, null);
    }

    private static Meta meta(
            TipoMeta tipo, long monto, FrecuenciaMeta frecuencia, Integer diaSemana,
            Integer intervaloDias, LocalDate inicio, LocalDate objetivo) {
        Meta meta = Meta.builder().build();
        meta.reemplazar(tipo, monto, frecuencia, diaSemana, intervaloDias, inicio, objetivo);
        return meta;
    }

    /** Fila con el saldo inicial dado: {@code disponible = inicial + asignado + actividad}. */
    private static FilaMes fila(long inicial, long asignado, long actividad) {
        return new FilaMes(asignado, actividad, inicial + asignado + actividad);
    }

    private static Resultado calcular(Meta meta, YearMonth mes, FilaMes fila) {
        return CalculoMeta.calcular(meta, mes, fila, false);
    }

    // ---------- A ----------

    @Test
    void ejemploAMontoMensualConAsignadoParcialFalta() {
        Resultado resultado = calcular(mensual(100_000L), OCTUBRE, fila(0, 60_000L, 0));

        assertThat(resultado.necesidad()).isEqualTo(100_000L);
        assertThat(resultado.faltante()).isEqualTo(40_000L);
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.FALTA);
    }

    @Test
    void ejemploAMontoMensualConElAsignadoCompletoEstaFinanciada() {
        Resultado resultado = calcular(mensual(100_000L), OCTUBRE, fila(0, 100_000L, 0));

        assertThat(resultado.faltante()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.FINANCIADA);
    }

    @Test
    void unAsignadoMayorQueLaNecesidadNoDaFaltanteNegativo() {
        Resultado resultado = calcular(mensual(100_000L), OCTUBRE, fila(0, 150_000L, 0));

        assertThat(resultado.faltante()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.FINANCIADA);
    }

    // ---------- B ----------

    @Test
    void ejemploBSemanalLosLunesDeOctubreYNoviembre() {
        Meta lunes = meta(TipoMeta.MONTO_MENSUAL, 20_000L, FrecuenciaMeta.SEMANAL, 1, null, null,
                null);

        assertThat(calcular(lunes, OCTUBRE, fila(0, 0, 0)).necesidad()).isEqualTo(80_000L);
        assertThat(calcular(lunes, YearMonth.of(2026, 11), fila(0, 0, 0)).necesidad())
                .isEqualTo(100_000L);
    }

    // ---------- C ----------

    @Test
    void ejemploCPersonalizadaCadaCatorceDias() {
        Meta cada14 = meta(TipoMeta.MONTO_MENSUAL, 10_000L, FrecuenciaMeta.PERSONALIZADA, null,
                14, LocalDate.of(2026, 10, 2), null);

        assertThat(calcular(cada14, YearMonth.of(2026, 9), fila(0, 0, 0)).necesidad()).isZero();
        assertThat(calcular(cada14, OCTUBRE, fila(0, 0, 0)).necesidad()).isEqualTo(30_000L);
        assertThat(calcular(cada14, YearMonth.of(2026, 11), fila(0, 0, 0)).necesidad())
                .isEqualTo(20_000L);
    }

    @Test
    void ejemploC2IntervaloLargoFebreroDa0YDiciembreDa20000() {
        Meta cada30 = meta(TipoMeta.MONTO_MENSUAL, 10_000L, FrecuenciaMeta.PERSONALIZADA, null,
                30, LocalDate.of(2026, 10, 2), null);

        assertThat(calcular(cada30, YearMonth.of(2027, 2), fila(0, 0, 0)).necesidad()).isZero();
        assertThat(calcular(cada30, YearMonth.of(2026, 12), fila(0, 0, 0)).necesidad())
                .isEqualTo(20_000L);
    }

    // ---------- D ----------

    private static Meta paraFecha(long monto) {
        return meta(TipoMeta.MONTO_PARA_FECHA, monto, null, null, null, null,
                LocalDate.of(2026, 12, 15));
    }

    @Test
    void ejemploDMontoParaFechaRepartidoEnLosMesesRestantes() {
        assertThat(calcular(paraFecha(600_000L), OCTUBRE, fila(0, 0, 0)).necesidad())
                .isEqualTo(200_000L);
        assertThat(calcular(paraFecha(600_000L), OCTUBRE, fila(150_000L, 0, 0)).necesidad())
                .isEqualTo(150_000L);
    }

    @Test
    void ejemploDElRedondeoEsHaciaArriba() {
        assertThat(calcular(paraFecha(100_000L), OCTUBRE, fila(0, 0, 0)).necesidad())
                .isEqualTo(33_334L);
    }

    @Test
    void ejemploDDespuesDeLaFechaObjetivoSeNecesitaTodoLoQueFalta() {
        assertThat(calcular(paraFecha(600_000L), YearMonth.of(2027, 1), fila(450_000L, 0, 0))
                .necesidad()).isEqualTo(150_000L);
    }

    @Test
    void montoParaFechaYaCubiertoNoNecesitaNada() {
        Resultado resultado = calcular(paraFecha(600_000L), OCTUBRE, fila(700_000L, 0, 0));

        assertThat(resultado.necesidad()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.FINANCIADA);
    }

    @Test
    void elInicialSeObtieneDelDisponibleSinContarAsignadoNiActividad() {
        // inicial 150000, asignado 50000, actividad -20000: disponible 180000.
        FilaMes fila = fila(150_000L, 50_000L, -20_000L);
        Resultado resultado = calcular(paraFecha(600_000L), OCTUBRE, fila);

        assertThat(resultado.necesidad()).isEqualTo(150_000L);
        assertThat(resultado.faltante()).isEqualTo(100_000L);
    }

    // ---------- E ----------

    private static Meta saldo(long monto) {
        return meta(TipoMeta.SALDO_OBJETIVO, monto, null, null, null, null, null);
    }

    @Test
    void ejemploESaldoObjetivoConInicialParcial() {
        Resultado sinAsignar = calcular(saldo(300_000L), OCTUBRE, fila(120_000L, 0, 0));
        assertThat(sinAsignar.necesidad()).isEqualTo(180_000L);
        assertThat(sinAsignar.faltante()).isEqualTo(180_000L);
        assertThat(sinAsignar.estado()).isEqualTo(EstadoMeta.FALTA);

        Resultado asignado = calcular(saldo(300_000L), OCTUBRE, fila(120_000L, 180_000L, 0));
        assertThat(asignado.faltante()).isZero();
        assertThat(asignado.estado()).isEqualTo(EstadoMeta.FINANCIADA);
    }

    @Test
    void ejemploESaldoObjetivoYaSuperadoNoNecesitaNada() {
        Resultado resultado = calcular(saldo(300_000L), OCTUBRE, fila(400_000L, 0, 0));

        assertThat(resultado.necesidad()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.FINANCIADA);
    }

    // ---------- F, G y prioridad de estados ----------

    @Test
    void ejemploFUnDisponibleNegativoEsSobregastadaAunqueEsteFinanciadaOPospuesta() {
        // asignado 100000 y actividad -105000: disponible -5000, faltante 0.
        FilaMes sobregastada = new FilaMes(100_000L, -105_000L, -5_000L);

        Meta meta = mensual(100_000L);
        Resultado financiada = CalculoMeta.calcular(meta, OCTUBRE, sobregastada, false);
        Resultado pospuesta = CalculoMeta.calcular(meta, OCTUBRE, sobregastada, true);

        assertThat(financiada.faltante()).isZero();
        assertThat(financiada.estado()).isEqualTo(EstadoMeta.SOBREGASTADA);
        assertThat(pospuesta.estado()).isEqualTo(EstadoMeta.SOBREGASTADA);
    }

    @Test
    void sobregastadaConFaltanteSigueSiendoSobregastada() {
        FilaMes fila = new FilaMes(0L, -5_000L, -5_000L);
        Resultado resultado = calcular(mensual(100_000L), OCTUBRE, fila);

        assertThat(resultado.faltante()).isEqualTo(100_000L);
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.SOBREGASTADA);
    }

    @Test
    void ejemploGPospuestaTieneNecesidadYFaltanteCero() {
        Resultado resultado = CalculoMeta.calcular(
                mensual(100_000L), OCTUBRE, fila(0, 0, 0), true);

        assertThat(resultado.necesidad()).isZero();
        assertThat(resultado.faltante()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoMeta.POSPUESTA);
    }

    @Test
    void pospuestaTienePrioridadSobreFinanciadaYFalta() {
        assertThat(CalculoMeta.calcular(mensual(100_000L), OCTUBRE, fila(0, 500_000L, 0), true)
                .estado()).isEqualTo(EstadoMeta.POSPUESTA);
        assertThat(CalculoMeta.calcular(saldo(100_000L), OCTUBRE, fila(0, 0, 0), true).estado())
                .isEqualTo(EstadoMeta.POSPUESTA);
    }

    @Test
    void elMesSiguienteAUnaPospuestaSeCalculaConNormalidad() {
        Meta meta = mensual(100_000L);

        assertThat(CalculoMeta.calcular(meta, OCTUBRE, fila(0, 0, 0), true).necesidad()).isZero();
        Resultado noviembre = CalculoMeta.calcular(
                meta, YearMonth.of(2026, 11), fila(0, 0, 0), false);
        assertThat(noviembre.necesidad()).isEqualTo(100_000L);
        assertThat(noviembre.estado()).isEqualTo(EstadoMeta.FALTA);
    }
}
