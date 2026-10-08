package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Los reportes reutilizan las reglas del presupuesto mensual: sus cifras de un mes deben
 * coincidir con las de las demás pantallas, comparadas por HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoincidenciaMesIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApoyoHttpReporte http;
    private Sesion ana;

    @BeforeEach
    void preparar() throws Exception {
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-coincidencia@ejemplo.com");
    }

    private JsonNode mes(String mes) throws Exception {
        return http.cuerpo(http.consultar(
                ana, ApoyoHttpReporte.rutaMes(ana.presupuestoId(), mes), "incluirOcultas", "true")
                .andExpect(status().isOk()));
    }

    /** Actividad de cada categoría del presupuesto mensual (las de pago aparte). */
    private Map<Long, Long> actividadDelMes(String mes, boolean pagoDeTarjeta) throws Exception {
        Map<Long, Long> actividad = new HashMap<>();
        for (JsonNode grupo : mes(mes).get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean() == pagoDeTarjeta) {
                    actividad.put(categoria.get("categoriaId").asLong(),
                            categoria.get("actividad").asLong());
                }
            }
        }
        return actividad;
    }

    private static Map<Long, Long> gastoPorCategoria(JsonNode reporte) {
        Map<Long, Long> gasto = new HashMap<>();
        for (JsonNode grupo : reporte.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                gasto.put(categoria.get("categoriaId").asLong(), categoria.get("total").asLong());
            }
        }
        return gasto;
    }

    @Test
    void elGastoPorCategoriaDeUnMesEsElNegativoDeLaActividadDelPresupuestoMensual()
            throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        long ahorro = http.crearCuenta(ana, "Ahorro", "AHORRO", false, 0L);
        long vida = http.crearGrupo(ana, "Vida diaria de prueba");
        long comida = http.crearCategoria(ana, vida, "Comida");
        long hogar = http.crearCategoria(ana, vida, "Hogar");
        long metas = http.crearCategoria(ana, "Metas de ahorro");
        http.transaccion(ana, corriente, "2026-10-01", 500_000L, null);
        http.transaccion(ana, corriente, "2026-10-02", -80_000L, comida);
        http.transaccion(ana, corriente, "2026-10-03", -5_000L, null);
        http.division(ana, corriente, "2026-10-04", -60_000L, List.of(
                new Long[] {comida, -40_000L}, new Long[] {hogar, -20_000L}));
        http.transferencia(ana, corriente, ahorro, "2026-10-05", 100_000L, metas);
        http.transaccion(ana, visa, "2026-10-06", -30_000L, comida);
        http.transaccion(ana, visa, "2026-10-07", 10_000L, comida);
        http.transferencia(ana, corriente, visa, "2026-10-08", 30_000L, null);
        http.transaccion(ana, corriente, "2026-09-20", -7_000L, hogar);

        JsonNode reporte = http.reporteOk(
                ana, "gasto-por-categoria", "desde", "2026-10", "hasta", "2026-10");

        Map<Long, Long> gasto = gastoPorCategoria(reporte);
        Map<Long, Long> actividad = actividadDelMes("2026-10", false);
        long sumaDeActividades = 0L;
        for (Map.Entry<Long, Long> categoria : actividad.entrySet()) {
            sumaDeActividades += categoria.getValue();
            assertThat(gasto.getOrDefault(categoria.getKey(), 0L))
                    .as("gasto de la categoría %d", categoria.getKey())
                    .isEqualTo(-categoria.getValue());
        }
        assertThat(gasto.keySet()).isSubsetOf(actividad.keySet());
        long sinCategoria = reporte.get("sinCategoria").get("total").asLong();
        assertThat(reporte.get("total").asLong()).isEqualTo(-sumaDeActividades + sinCategoria);
        assertThat(sinCategoria).isEqualTo(5_000L);
        // La reserva de pago de la tarjeta no tiene gasto propio en el reporte.
        assertThat(actividadDelMes("2026-10", true).values()).anyMatch(valor -> valor != 0L);
    }

    @Test
    void elGastoTotalDeAmbosReportesCoincideEnCualquierRango() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        long comida = http.crearCategoria(ana, "Comida");
        http.transaccion(ana, corriente, "2026-08-02", -80_000L, comida);
        http.transaccion(ana, visa, "2026-09-06", -30_000L, comida);
        http.transaccion(ana, corriente, "2026-09-07", -1_500L, null);
        http.division(ana, corriente, "2026-10-04", -9_000L, List.of(
                new Long[] {comida, -4_000L}, new Long[] {null, -5_000L}));

        JsonNode categorias = http.reporteOk(
                ana, "gasto-por-categoria", "desde", "2026-08", "hasta", "2026-11");
        JsonNode mensual = http.reporteOk(
                ana, "ingresos-gastos", "desde", "2026-08", "hasta", "2026-11");

        assertThat(categorias.get("total").asLong()).isEqualTo(120_500L);
        assertThat(mensual.get("gastos").asLong()).isEqualTo(categorias.get("total").asLong());
    }

    @Test
    void losIngresosAcumuladosMasLosSaldosInicialesSonLosIngresosDeListoParaAsignar()
            throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 200_000L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        long ahorro = http.crearCuenta(ana, "Ahorro", "AHORRO", false, 0L);
        long comida = http.crearCategoria(ana, "Comida");
        long metas = http.crearCategoria(ana, "Metas de ahorro");
        http.transaccion(ana, corriente, "2026-09-01", 300_000L, null);
        http.transaccion(ana, corriente, "2026-09-02", -50_000L, comida);
        http.transaccion(ana, visa, "2026-09-03", -20_000L, comida);
        http.transaccion(ana, corriente, "2026-10-01", 100_000L, null);
        http.transaccion(ana, corriente, "2026-10-02", -30_000L, comida);
        http.transferencia(ana, corriente, ahorro, "2026-10-03", 40_000L, metas);
        http.asignar(ana, "2026-09", comida, 70_000L);
        http.asignar(ana, "2026-10", comida, 30_000L);
        http.asignar(ana, "2026-10", metas, 40_000L);

        JsonNode mensual = http.reporteOk(
                ana, "ingresos-gastos", "desde", "2026-01", "hasta", "2026-10");

        long ingresosDelPresupuesto = mensual.get("ingresos").asLong() + 200_000L;
        long asignadoAcumulado = mes("2026-09").get("totalAsignado").asLong()
                + mes("2026-10").get("totalAsignado").asLong();
        long listoParaAsignar = mes("2026-10").get("listoParaAsignar").asLong();
        assertThat(mensual.get("ingresos").asLong()).isEqualTo(400_000L);
        assertThat(asignadoAcumulado).isEqualTo(140_000L);
        assertThat(ingresosDelPresupuesto).isEqualTo(listoParaAsignar + asignadoAcumulado);
    }

    @Test
    void lasMetasDelReporteSonLasDelEstadoDeMetasDeCadaMes() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        long pagoVisa = http.categoriaDePago(ana, visa);
        long mensual = http.crearCategoria(ana, "Mensual");
        long objetivo = http.crearCategoria(ana, "Objetivo");
        long paraFecha = http.crearCategoria(ana, "Para fecha");
        long pospuesta = http.crearCategoria(ana, "Pospuesta");
        long sobregastada = http.crearCategoria(ana, "Sobregastada");
        long oculta = http.crearCategoria(ana, "Oculta");
        http.transaccion(ana, corriente, "2026-07-01", 3_000_000L, null);
        http.guardarMeta(ana, mensual, ApoyoHttpReporte.metaMensual(100_000L));
        http.guardarMeta(ana, objetivo, ApoyoHttpReporte.saldoObjetivo(70_000L));
        http.guardarMeta(ana, paraFecha, ApoyoHttpReporte.paraFecha(240_000L, "2026-12-01"));
        http.guardarMeta(ana, pospuesta, ApoyoHttpReporte.metaMensual(30_000L));
        http.guardarMeta(ana, sobregastada, ApoyoHttpReporte.metaMensual(20_000L));
        http.guardarMeta(ana, oculta, ApoyoHttpReporte.metaMensual(10_000L));
        http.guardarMeta(ana, pagoVisa, ApoyoHttpReporte.metaMensual(40_000L));
        http.asignar(ana, "2026-08", mensual, 100_000L);
        http.asignar(ana, "2026-09", mensual, 60_000L);
        http.asignar(ana, "2026-08", objetivo, 30_000L);
        http.asignar(ana, "2026-10", objetivo, 20_000L);
        http.asignar(ana, "2026-09", paraFecha, 50_000L);
        http.asignar(ana, "2026-09", sobregastada, 5_000L);
        http.asignar(ana, "2026-10", pagoVisa, 15_000L);
        http.transaccion(ana, corriente, "2026-08-10", -40_000L, mensual);
        http.transaccion(ana, corriente, "2026-09-10", -25_000L, sobregastada);
        http.transaccion(ana, visa, "2026-09-12", -35_000L, mensual);
        http.transferencia(ana, corriente, visa, "2026-10-05", 20_000L, null);
        http.posponerMeta(ana, "2026-09", pospuesta);
        http.ocultarCategoria(ana, oculta);

        JsonNode reporte = http.reporteOk(ana, "metas", "desde", "2026-07", "hasta", "2026-11");

        assertThat(reporte.get("metas")).hasSize(7);
        for (String mes : List.of("2026-07", "2026-08", "2026-09", "2026-10", "2026-11")) {
            JsonNode estado = http.cuerpo(http.consultar(
                    ana, ApoyoHttpReporte.rutaMes(ana.presupuestoId(), mes) + "/metas",
                    "incluirOcultas", "true").andExpect(status().isOk()));
            assertThat(estado.get("metas")).as("metas de %s", mes).hasSize(7);
            for (JsonNode delMes : estado.get("metas")) {
                JsonNode delReporte = mesDeMeta(reporte, delMes.get("categoriaId").asLong(), mes);
                for (String campo : List.of("necesidad", "asignado", "disponible", "faltante")) {
                    assertThat(delReporte.get(campo).asLong())
                            .as("%s de %s en %s", campo, delMes.get("nombre").asString(), mes)
                            .isEqualTo(delMes.get(campo).asLong());
                }
                assertThat(delReporte.get("estado").asString())
                        .as("estado de %s en %s", delMes.get("nombre").asString(), mes)
                        .isEqualTo(delMes.get("estado").asString());
            }
        }
        assertThat(mesDeMeta(reporte, pospuesta, "2026-09").get("estado").asString())
                .isEqualTo("POSPUESTA");
        assertThat(mesDeMeta(reporte, sobregastada, "2026-09").get("estado").asString())
                .isEqualTo("SOBREGASTADA");
    }

    private static JsonNode mesDeMeta(JsonNode reporte, long categoriaId, String mes) {
        for (JsonNode meta : reporte.get("metas")) {
            if (meta.get("categoriaId").asLong() == categoriaId) {
                for (JsonNode delMes : meta.get("meses")) {
                    if (delMes.get("mes").asString().equals(mes)) {
                        return delMes;
                    }
                }
            }
        }
        throw new AssertionError("Sin meta " + categoriaId + " en " + mes);
    }

    /** Cuentas de todos los tipos, dentro y fuera del presupuesto, con movimientos a octubre. */
    private List<Long> cuentasConMovimientos() throws Exception {
        long corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 1_000_000L);
        long ahorro = http.crearCuenta(ana, "Ahorro", "AHORRO", false, 600_000L);
        long efectivo = http.crearCuenta(ana, "Efectivo", "EFECTIVO", true, 20_000L);
        long inversion = http.crearCuenta(ana, "Inversion", "INVERSION", false, 300_000L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -150_000L);
        long prestamo = http.crearCuenta(ana, "Prestamo", "PRESTAMO", false, -2_900_000L);
        long vieja = http.crearCuenta(ana, "Vieja", "EFECTIVO", true, 5_000L);
        long comida = http.crearCategoria(ana, "Comida");
        http.transaccion(ana, corriente, "2026-08-01", 400_000L, null);
        http.transaccion(ana, corriente, "2026-09-02", -80_000L, comida);
        http.transaccion(ana, visa, "2026-09-03", -30_000L, comida);
        http.transferencia(ana, corriente, visa, "2026-10-04", 30_000L, null);
        http.transferencia(ana, corriente, ahorro, "2026-10-05", 100_000L, comida);
        http.transaccion(ana, inversion, "2026-10-06", 12_000L, null);
        http.transaccion(ana, prestamo, "2026-10-07", 250_000L, null);
        http.transaccion(ana, efectivo, "2026-10-31", -3_000L, comida);
        http.transaccion(ana, vieja, "2026-09-30", -5_000L, comida);
        http.cerrarCuenta(ana, vieja);
        return List.of(corriente, ahorro, efectivo, inversion, visa, prestamo, vieja);
    }

    @Test
    void elSaldoFinalDeLaEvolucionDeCadaCuentaEsSuSaldoDelListadoDeSaldos() throws Exception {
        List<Long> cuentas = cuentasConMovimientos();
        Map<Long, Long> saldos = http.saldos(ana);

        for (long cuentaId : cuentas) {
            JsonNode evolucion = http.reporteOk(
                    ana, "cuentas/" + cuentaId + "/evolucion-saldo",
                    "desde", "2026-06", "hasta", "2026-10");
            JsonNode meses = evolucion.get("meses");

            assertThat(meses.get(meses.size() - 1).get("saldo").asLong())
                    .as("saldo final de la cuenta %d", cuentaId)
                    .isEqualTo(saldos.get(cuentaId));
        }
    }

    @Test
    void elPatrimonioDelUltimoMesEsLaSumaConSignoDeLosSaldosDeTodasLasCuentas() throws Exception {
        cuentasConMovimientos();
        long sumaDeSaldos = http.saldos(ana).values().stream().mapToLong(Long::longValue).sum();

        JsonNode patrimonio = http.reporteOk(
                ana, "patrimonio", "desde", "2026-06", "hasta", "2026-10");
        JsonNode octubre = patrimonio.get("meses").get(4);

        assertThat(octubre.get("mes").asString()).isEqualTo("2026-10");
        assertThat(octubre.get("patrimonio").asLong()).isEqualTo(sumaDeSaldos);
        assertThat(octubre.get("activos").asLong() - octubre.get("pasivos").asLong())
                .isEqualTo(sumaDeSaldos);
    }

    @Test
    void elPatrimonioDeCadaMesEsLaSumaDeLasEvolucionesDeTodasLasCuentas() throws Exception {
        List<Long> cuentas = cuentasConMovimientos();
        long[] sumaPorMes = new long[5];
        for (long cuentaId : cuentas) {
            JsonNode meses = http.reporteOk(
                    ana, "cuentas/" + cuentaId + "/evolucion-saldo",
                    "desde", "2026-06", "hasta", "2026-10").get("meses");
            for (int i = 0; i < 5; i++) {
                sumaPorMes[i] += meses.get(i).get("saldo").asLong();
            }
        }

        JsonNode patrimonio = http.reporteOk(
                ana, "patrimonio", "desde", "2026-06", "hasta", "2026-10").get("meses");

        for (int i = 0; i < 5; i++) {
            assertThat(patrimonio.get(i).get("patrimonio").asLong())
                    .as("patrimonio de %s", patrimonio.get(i).get("mes").asString())
                    .isEqualTo(sumaPorMes[i]);
        }
    }
}
