package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import java.util.List;
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
class GastoReporteIntegracionTest {

    private static final String GASTO = "gasto-por-categoria";
    private static final String INGRESOS_GASTOS = "ingresos-gastos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApoyoHttpReporte http;
    private Sesion ana;
    private long corriente;
    private long visa;
    private long ahorro;
    private long vidaDiaria;
    private long comida;
    private long hogar;
    private long metasDeAhorro;

    @BeforeEach
    void preparar() throws Exception {
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-gasto@ejemplo.com");
        corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        ahorro = http.crearCuenta(ana, "Ahorro", "AHORRO", false, 0L);
        vidaDiaria = http.crearGrupo(ana, "Vida diaria de prueba");
        comida = http.crearCategoria(ana, vidaDiaria, "Comida");
        hogar = http.crearCategoria(ana, vidaDiaria, "Hogar");
        metasDeAhorro = http.crearCategoria(ana, "Metas de ahorro");
    }

    /** El ejemplo de octubre de la spec (milésimas). */
    private void ejemploDeOctubre() throws Exception {
        http.transaccion(ana, corriente, "2026-10-01", 500_000L, null);
        http.transaccion(ana, corriente, "2026-10-02", -80_000L, comida);
        http.transaccion(ana, corriente, "2026-10-03", -5_000L, null);
        http.division(ana, corriente, "2026-10-04", -60_000L, List.of(
                new Long[] {comida, -40_000L}, new Long[] {hogar, -20_000L}));
        http.transferencia(ana, corriente, ahorro, "2026-10-05", 100_000L, metasDeAhorro);
        http.transaccion(ana, visa, "2026-10-06", -30_000L, comida);
        http.transaccion(ana, visa, "2026-10-07", 10_000L, comida);
        http.transferencia(ana, corriente, visa, "2026-10-08", 30_000L, null);
        http.transaccion(ana, ahorro, "2026-10-09", 2_000L, null);
    }

    private static JsonNode categoria(JsonNode respuesta, String nombre) {
        for (JsonNode grupo : respuesta.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("nombre").asString().equals(nombre)) {
                    return categoria;
                }
            }
        }
        return null;
    }

    private static JsonNode grupo(JsonNode respuesta, String nombre) {
        for (JsonNode grupo : respuesta.get("grupos")) {
            if (grupo.get("nombre").asString().equals(nombre)) {
                return grupo;
            }
        }
        return null;
    }

    @Test
    void elEjemploDeOctubreDaLosTotalesYLosPorcentajesDeLaSpec() throws Exception {
        ejemploDeOctubre();

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(respuesta.get("desde").asString()).isEqualTo("2026-10");
        assertThat(respuesta.get("hasta").asString()).isEqualTo("2026-10");
        assertThat(respuesta.get("total").asLong()).isEqualTo(265_000L);
        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(140_000L);
        assertThat(categoria(respuesta, "Comida").get("porcentaje").asLong()).isEqualTo(5283L);
        assertThat(categoria(respuesta, "Hogar").get("total").asLong()).isEqualTo(20_000L);
        assertThat(categoria(respuesta, "Hogar").get("porcentaje").asLong()).isEqualTo(755L);
        assertThat(categoria(respuesta, "Metas de ahorro").get("total").asLong())
                .isEqualTo(100_000L);
        assertThat(categoria(respuesta, "Metas de ahorro").get("porcentaje").asLong())
                .isEqualTo(3774L);
        assertThat(respuesta.get("sinCategoria").get("total").asLong()).isEqualTo(5_000L);
        assertThat(respuesta.get("sinCategoria").get("porcentaje").asLong()).isEqualTo(189L);
        assertThat(grupo(respuesta, "Vida diaria de prueba").get("total").asLong())
                .isEqualTo(160_000L);
    }

    @Test
    void elGastoConTarjetaVaEnSuCategoriaYLaCategoriaDePagoNoAparece() throws Exception {
        ejemploDeOctubre();

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Pago: Visa")).isNull();
        assertThat(grupo(respuesta, "Pagos de tarjetas de crédito")).isNull();
        // 80000 supermercado + 40000 parte dividida + 30000 con la tarjeta - 10000 reembolso
        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(140_000L);
    }

    @Test
    void losIngresosDelEjemploSon500000SinElPagoDeTarjetaNiLosInteresesDeAhorro()
            throws Exception {
        ejemploDeOctubre();

        JsonNode respuesta =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-11");

        assertThat(respuesta.get("meses")).hasSize(2);
        JsonNode octubre = respuesta.get("meses").get(0);
        assertThat(octubre.get("mes").asString()).isEqualTo("2026-10");
        assertThat(octubre.get("ingresos").asLong()).isEqualTo(500_000L);
        assertThat(octubre.get("gastos").asLong()).isEqualTo(265_000L);
        assertThat(octubre.get("neto").asLong()).isEqualTo(235_000L);
        JsonNode noviembre = respuesta.get("meses").get(1);
        assertThat(noviembre.get("ingresos").asLong()).isZero();
        assertThat(noviembre.get("gastos").asLong()).isZero();
        assertThat(noviembre.get("neto").asLong()).isZero();
        assertThat(respuesta.get("ingresos").asLong()).isEqualTo(500_000L);
        assertThat(respuesta.get("gastos").asLong()).isEqualTo(265_000L);
        assertThat(respuesta.get("neto").asLong()).isEqualTo(235_000L);
    }

    @Test
    void elGastoTotalDelReporteDeCategoriasIgualaLosGastosDeIngresosContraGastos()
            throws Exception {
        ejemploDeOctubre();
        http.transaccion(ana, corriente, "2026-09-15", -12_000L, hogar);

        JsonNode categorias = http.reporteOk(ana, GASTO, "desde", "2026-09", "hasta", "2026-10");
        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-09", "hasta", "2026-10");

        assertThat(categorias.get("total").asLong()).isEqualTo(277_000L);
        assertThat(mensual.get("gastos").asLong()).isEqualTo(categorias.get("total").asLong());
    }

    @Test
    void unaTransferenciaEntreCuentasDelPresupuestoNoEsGastoNiIngreso() throws Exception {
        long efectivo = http.crearCuenta(ana, "Efectivo", "EFECTIVO", true, 0L);
        http.transferencia(ana, corriente, efectivo, "2026-10-05", 50_000L, null);

        JsonNode categorias = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");
        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categorias.get("total").asLong()).isZero();
        assertThat(categorias.get("sinCategoria").get("total").asLong()).isZero();
        assertThat(categorias.get("grupos")).isEmpty();
        assertThat(mensual.get("ingresos").asLong()).isZero();
        assertThat(mensual.get("gastos").asLong()).isZero();
    }

    @Test
    void unaEntradaSinCategoriaDesdeUnaCuentaFueraDelPresupuestoEsIngreso() throws Exception {
        http.transferencia(ana, ahorro, corriente, "2026-10-05", 20_000L, null);

        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-10");

        assertThat(mensual.get("ingresos").asLong()).isEqualTo(20_000L);
        assertThat(mensual.get("gastos").asLong()).isZero();
    }

    @Test
    void unaEntradaSinCategoriaEnUnaTarjetaNoSumaAIngresosNiAGastos() throws Exception {
        http.transaccion(ana, visa, "2026-10-05", 10_000L, null);

        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-10");

        assertThat(mensual.get("ingresos").asLong()).isZero();
        assertThat(mensual.get("gastos").asLong()).isZero();
    }

    @Test
    void laParteDeUnaDivisionSinCategoriaVaAlCuboSinCategoria() throws Exception {
        http.division(ana, corriente, "2026-10-04", -30_000L, List.of(
                new Long[] {comida, -20_000L}, new Long[] {null, -10_000L}));

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(20_000L);
        assertThat(respuesta.get("sinCategoria").get("total").asLong()).isEqualTo(10_000L);
        assertThat(respuesta.get("total").asLong()).isEqualTo(30_000L);
    }

    @Test
    void unaCategoriaOcultaConActividadApareceConSuGasto() throws Exception {
        http.transaccion(ana, corriente, "2026-10-04", -7_000L, hogar);
        http.ocultarCategoria(ana, hogar);

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Hogar").get("total").asLong()).isEqualTo(7_000L);
        assertThat(categoria(respuesta, "Hogar").get("oculta").asBoolean()).isTrue();
    }

    @Test
    void lasCuentasCerradasDelPresupuestoSiguenContando() throws Exception {
        long vieja = http.crearCuenta(ana, "Vieja", "EFECTIVO", true, 0L);
        http.transaccion(ana, vieja, "2026-10-04", -9_000L, comida);
        http.cerrarCuenta(ana, vieja);

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(9_000L);
    }

    @Test
    void lasCuentasFueraDelPresupuestoNoContanComoGastoNiComoIngreso() throws Exception {
        http.transaccion(ana, ahorro, "2026-10-04", -9_000L, null);
        http.transaccion(ana, ahorro, "2026-10-05", 4_000L, null);

        JsonNode categorias = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");
        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categorias.get("total").asLong()).isZero();
        assertThat(mensual.get("ingresos").asLong()).isZero();
        assertThat(mensual.get("gastos").asLong()).isZero();
    }

    @Test
    void unRangoDeVariosMesesSumaLosMeses() throws Exception {
        http.transaccion(ana, corriente, "2026-09-10", -100_000L, comida);
        http.transaccion(ana, corriente, "2026-10-10", -50_000L, comida);
        http.transaccion(ana, corriente, "2026-08-31", -1_000_000L, comida);
        http.transaccion(ana, corriente, "2026-11-01", -2_000_000L, comida);

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-09", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(150_000L);
        assertThat(respuesta.get("total").asLong()).isEqualTo(150_000L);
    }

    @Test
    void unReembolsoMayorQueElGastoDejaLaCategoriaNegativa() throws Exception {
        http.transaccion(ana, corriente, "2026-10-04", -10_000L, comida);
        http.transaccion(ana, corriente, "2026-10-05", 30_000L, comida);

        JsonNode respuesta = http.reporteOk(ana, GASTO, "desde", "2026-10", "hasta", "2026-10");

        assertThat(categoria(respuesta, "Comida").get("total").asLong()).isEqualTo(-20_000L);
        assertThat(respuesta.get("total").asLong()).isEqualTo(-20_000L);
        assertThat(categoria(respuesta, "Comida").get("porcentaje").asLong()).isZero();
    }

    @Test
    void unPresupuestoSinMovimientosDevuelveCerosYMesesCompletos() throws Exception {
        JsonNode categorias = http.reporteOk(ana, GASTO, "desde", "2026-01", "hasta", "2026-03");
        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-01", "hasta", "2026-03");

        assertThat(categorias.get("total").asLong()).isZero();
        assertThat(categorias.get("grupos")).isEmpty();
        assertThat(categorias.get("sinCategoria").get("total").asLong()).isZero();
        assertThat(categorias.get("sinCategoria").get("porcentaje").asLong()).isZero();
        assertThat(mensual.get("meses")).hasSize(3);
        for (JsonNode mes : mensual.get("meses")) {
            assertThat(mes.get("ingresos").asLong()).isZero();
            assertThat(mes.get("gastos").asLong()).isZero();
            assertThat(mes.get("neto").asLong()).isZero();
        }
        assertThat(mensual.get("meses").get(0).get("mes").asString()).isEqualTo("2026-01");
        assertThat(mensual.get("meses").get(2).get("mes").asString()).isEqualTo("2026-03");
    }

    @Test
    void laFronteraDeMesSeRespetaEnAmbosReportes() throws Exception {
        http.transaccion(ana, corriente, "2026-10-31", -3_000L, comida);
        http.transaccion(ana, corriente, "2026-11-01", -5_000L, comida);

        JsonNode mensual =
                http.reporteOk(ana, INGRESOS_GASTOS, "desde", "2026-10", "hasta", "2026-11");

        assertThat(mensual.get("meses").get(0).get("gastos").asLong()).isEqualTo(3_000L);
        assertThat(mensual.get("meses").get(1).get("gastos").asLong()).isEqualTo(5_000L);
    }
}
