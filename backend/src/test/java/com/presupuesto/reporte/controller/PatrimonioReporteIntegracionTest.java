package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatrimonioReporteIntegracionTest {

    private static final String PATRIMONIO = "patrimonio";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApoyoHttpReporte http;
    private Sesion ana;

    @BeforeEach
    void preparar() throws Exception {
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-patrimonio@ejemplo.com");
    }

    private JsonNode patrimonio(String desde, String hasta) throws Exception {
        return http.reporteOk(ana, PATRIMONIO, "desde", desde, "hasta", hasta);
    }

    private JsonNode evolucion(long cuentaId, String desde, String hasta) throws Exception {
        return http.reporteOk(
                ana, "cuentas/" + cuentaId + "/evolucion-saldo", "desde", desde, "hasta", hasta);
    }

    private static void esperarMes(
            JsonNode mes, String nombre, long activos, long pasivos, long patrimonio) {
        assertThat(mes.get("mes").asString()).isEqualTo(nombre);
        assertThat(mes.get("activos").asLong()).as("activos").isEqualTo(activos);
        assertThat(mes.get("pasivos").asLong()).as("pasivos").isEqualTo(pasivos);
        assertThat(mes.get("patrimonio").asLong()).as("patrimonio").isEqualTo(patrimonio);
    }

    private static void esperarSaldo(
            JsonNode mes, String nombre, long entradas, long salidas, long saldo) {
        assertThat(mes.get("mes").asString()).isEqualTo(nombre);
        assertThat(mes.get("entradas").asLong()).as("entradas").isEqualTo(entradas);
        assertThat(mes.get("salidas").asLong()).as("salidas").isEqualTo(salidas);
        assertThat(mes.get("saldo").asLong()).as("saldo").isEqualTo(saldo);
    }

    // ---------- patrimonio ----------

    @Test
    void elEjemploDeLaSpecDa1800000_3050000_y_menos1250000() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 1_000_000L);
        http.crearCuenta(ana, "Ahorro", "AHORRO", false, 600_000L);
        http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -150_000L);
        http.crearCuenta(ana, "Prestamo", "PRESTAMO", false, -2_900_000L);
        http.transaccion(ana, corriente, "2026-10-15", 200_000L, null);

        JsonNode respuesta = patrimonio("2026-10", "2026-10");

        assertThat(respuesta.get("desde").asString()).isEqualTo("2026-10");
        assertThat(respuesta.get("hasta").asString()).isEqualTo("2026-10");
        assertThat(respuesta.get("meses")).hasSize(1);
        esperarMes(respuesta.get("meses").get(0), "2026-10", 1_800_000L, 3_050_000L, -1_250_000L);
    }

    @Test
    void incluyeLosSeisTiposDeCuentaDentroYFueraDelPresupuesto() throws Exception {
        http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100L);
        http.crearCuenta(ana, "Ahorro", "AHORRO", false, 20_000L);
        http.crearCuenta(ana, "Efectivo", "EFECTIVO", true, 300L);
        http.crearCuenta(ana, "Inversion", "INVERSION", false, 4_000_000L);
        http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -50_000L);
        http.crearCuenta(ana, "Prestamo", "PRESTAMO", false, -7_000L);

        JsonNode mes = patrimonio("2026-10", "2026-10").get("meses").get(0);

        esperarMes(mes, "2026-10", 4_020_400L, 57_000L, 3_963_400L);
    }

    @Test
    void unaCuentaCerradaConSaldoSigueContando() throws Exception {
        long vieja = http.crearCuenta(ana, "Vieja", "EFECTIVO", true, 50_000L);
        http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 10_000L);
        http.cerrarCuenta(ana, vieja);

        JsonNode mes = patrimonio("2026-10", "2026-10").get("meses").get(0);

        esperarMes(mes, "2026-10", 60_000L, 0L, 60_000L);
    }

    @Test
    void losMesesSinMovimientosRepitenElSaldoDelMesAnterior() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        http.transaccion(ana, corriente, "2026-09-10", 80_000L, null);
        http.transaccion(ana, corriente, "2026-09-11", -30_000L, null);

        JsonNode meses = patrimonio("2026-09", "2026-11").get("meses");

        assertThat(meses).hasSize(3);
        esperarMes(meses.get(0), "2026-09", 50_000L, 0L, 50_000L);
        esperarMes(meses.get(1), "2026-10", 50_000L, 0L, 50_000L);
        esperarMes(meses.get(2), "2026-11", 50_000L, 0L, 50_000L);
    }

    @Test
    void antesDeLaPrimeraTransaccionLaCuentaAportaSuSaldoInicial() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        http.transaccion(ana, corriente, "2026-10-10", 25_000L, null);

        JsonNode meses = patrimonio("2026-08", "2026-10").get("meses");

        esperarMes(meses.get(0), "2026-08", 100_000L, 0L, 100_000L);
        esperarMes(meses.get(2), "2026-10", 125_000L, 0L, 125_000L);
    }

    @Test
    void unRangoQueEmpiezaDespuesDeMovimientosPreviosParteDelSaldoDeApertura() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        http.transaccion(ana, corriente, "2025-11-10", 40_000L, null);
        http.transaccion(ana, corriente, "2025-12-10", -10_000L, null);
        http.transaccion(ana, corriente, "2026-01-10", 5_000L, null);
        http.transaccion(ana, corriente, "2026-01-11", -2_000L, null);

        JsonNode meses = patrimonio("2026-01", "2026-02").get("meses");

        esperarMes(meses.get(0), "2026-01", 133_000L, 0L, 133_000L);
        esperarMes(meses.get(1), "2026-02", 133_000L, 0L, 133_000L);
    }

    @Test
    void unaTransferenciaEntreCuentasNoCambiaElPatrimonio() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 500_000L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -80_000L);
        http.transferencia(ana, corriente, visa, "2026-10-08", 30_000L, null);

        JsonNode mes = patrimonio("2026-10", "2026-10").get("meses").get(0);

        esperarMes(mes, "2026-10", 470_000L, 50_000L, 420_000L);
    }

    @Test
    void unPresupuestoSinCuentasDevuelveCerosEnTodosLosMeses() throws Exception {
        JsonNode meses = patrimonio("2026-01", "2026-03").get("meses");

        assertThat(meses).hasSize(3);
        for (JsonNode mes : meses) {
            esperarMes(mes, mes.get("mes").asString(), 0L, 0L, 0L);
        }
    }

    @Test
    void losDatosDeOtraPersonaNoEntranEnElPatrimonio() throws Exception {
        http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        Sesion beto = http.registrar("beto-patrimonio@ejemplo.com");
        http.crearCuenta(beto, "Corriente", "CORRIENTE", true, 999_000L);

        JsonNode deAna = patrimonio("2026-10", "2026-10").get("meses").get(0);
        JsonNode deBeto = http.cuerpo(http.reporte(
                beto, PATRIMONIO, "desde", "2026-10", "hasta", "2026-10"));

        assertThat(deAna.get("patrimonio").asLong()).isEqualTo(100_000L);
        assertThat(deBeto.get("meses").get(0).get("patrimonio").asLong()).isEqualTo(999_000L);
    }

    // ---------- evolución del saldo ----------

    @Test
    void laEvolucionDelEjemploDeLaSpec() throws Exception {
        long cuenta = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        http.transaccion(ana, cuenta, "2026-09-05", 50_000L, null);
        http.transaccion(ana, cuenta, "2026-09-06", -20_000L, null);
        http.transaccion(ana, cuenta, "2026-11-07", -30_000L, null);

        JsonNode respuesta = evolucion(cuenta, "2026-09", "2026-11");

        assertThat(respuesta.get("cuentaId").asLong()).isEqualTo(cuenta);
        assertThat(respuesta.get("nombre").asString()).isEqualTo("Corriente");
        assertThat(respuesta.get("tipo").asString()).isEqualTo("CORRIENTE");
        assertThat(respuesta.get("enPresupuesto").asBoolean()).isTrue();
        assertThat(respuesta.get("cerrada").asBoolean()).isFalse();
        assertThat(respuesta.get("saldoInicial").asLong()).isEqualTo(100_000L);
        assertThat(respuesta.get("desde").asString()).isEqualTo("2026-09");
        assertThat(respuesta.get("meses")).hasSize(3);
        esperarSaldo(respuesta.get("meses").get(0), "2026-09", 50_000L, -20_000L, 130_000L);
        esperarSaldo(respuesta.get("meses").get(1), "2026-10", 0L, 0L, 130_000L);
        esperarSaldo(respuesta.get("meses").get(2), "2026-11", 0L, -30_000L, 100_000L);
    }

    @Test
    void laEvolucionConUnRangoPosteriorAMovimientosPreviosIncluyeLaApertura() throws Exception {
        long cuenta = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        http.transaccion(ana, cuenta, "2025-11-10", 40_000L, null);
        http.transaccion(ana, cuenta, "2025-12-10", -10_000L, null);
        http.transaccion(ana, cuenta, "2026-01-10", 5_000L, null);
        http.transaccion(ana, cuenta, "2026-01-11", -2_000L, null);

        JsonNode meses = evolucion(cuenta, "2026-01", "2026-02").get("meses");

        assertThat(meses).hasSize(2);
        esperarSaldo(meses.get(0), "2026-01", 5_000L, -2_000L, 133_000L);
        esperarSaldo(meses.get(1), "2026-02", 0L, 0L, 133_000L);
    }

    @Test
    void laEvolucionDeUnaTarjetaMuestraLaDeudaComoSaldoNegativo() throws Exception {
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -200_000L);
        http.transaccion(ana, visa, "2026-10-10", 50_000L, null);

        JsonNode mes = evolucion(visa, "2026-10", "2026-10").get("meses").get(0);

        esperarSaldo(mes, "2026-10", 50_000L, 0L, -150_000L);
    }

    @Test
    void laEvolucionFuncionaParaUnaCuentaFueraDelPresupuestoYParaUnaCerrada() throws Exception {
        long fuera = http.crearCuenta(ana, "Inversion", "INVERSION", false, 30_000L);
        http.transaccion(ana, fuera, "2026-10-10", 5_000L, null);
        long vieja = http.crearCuenta(ana, "Vieja", "EFECTIVO", true, 10_000L);
        http.cerrarCuenta(ana, vieja);

        JsonNode deFuera = evolucion(fuera, "2026-10", "2026-10");
        JsonNode deVieja = evolucion(vieja, "2026-10", "2026-10");

        assertThat(deFuera.get("enPresupuesto").asBoolean()).isFalse();
        esperarSaldo(deFuera.get("meses").get(0), "2026-10", 5_000L, 0L, 35_000L);
        assertThat(deVieja.get("cerrada").asBoolean()).isTrue();
        esperarSaldo(deVieja.get("meses").get(0), "2026-10", 0L, 0L, 10_000L);
    }

    @Test
    void cadaPataDeUnaTransferenciaTraeSuSalidaOSuEntrada() throws Exception {
        long origen = http.crearCuenta(ana, "Origen", "CORRIENTE", true, 100_000L);
        long destino = http.crearCuenta(ana, "Destino", "AHORRO", true, 0L);
        http.transferencia(ana, origen, destino, "2026-10-10", 40_000L, null);

        JsonNode deOrigen = evolucion(origen, "2026-10", "2026-10").get("meses").get(0);
        JsonNode deDestino = evolucion(destino, "2026-10", "2026-10").get("meses").get(0);

        esperarSaldo(deOrigen, "2026-10", 0L, -40_000L, 60_000L);
        esperarSaldo(deDestino, "2026-10", 40_000L, 0L, 40_000L);
    }

    @Test
    void unaCuentaAjenaODeOtroPresupuestoEs404AunConParametrosInvalidos() throws Exception {
        long propia = http.crearCuenta(ana, "Propia", "CORRIENTE", true, 0L);
        Sesion beto = http.registrar("beto-evolucion@ejemplo.com");
        long deBeto = http.crearCuenta(beto, "DeBeto", "CORRIENTE", true, 0L);
        long otroPresupuesto = http.idDe(http.enviar(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        ApoyoHttpReporte.RUTA_PRESUPUESTOS),
                ana, java.util.Map.of("nombre", "Otro")));

        ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                ana, "cuentas/" + deBeto + "/evolucion-saldo", "desde", "2026-10", "hasta",
                "2026-10"));
        ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                ana, "cuentas/" + deBeto + "/evolucion-saldo", "desde", "2026-13"));
        ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                ana, otroPresupuesto, "cuentas/" + propia + "/evolucion-saldo", "desde",
                "2026-10", "hasta", "2026-10"));
        ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                ana, "cuentas/999999999/evolucion-saldo", "desde", "2026-10", "hasta",
                "2026-10"));
    }
}
