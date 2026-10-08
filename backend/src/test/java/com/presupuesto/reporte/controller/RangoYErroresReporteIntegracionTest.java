package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Autenticación, aislamiento, orden de errores y rango de los reportes por rango de meses. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RangoYErroresReporteIntegracionTest {

    private static final long PRESUPUESTO_INEXISTENTE = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApoyoHttpReporte http;
    private Sesion ana;
    private long cuentaDeAna;

    @BeforeEach
    void preparar() throws Exception {
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-rango@ejemplo.com");
        cuentaDeAna = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
    }

    /** Las rutas de los reportes por rango, relativas a {@code .../reportes/}. */
    private List<String> rutas() {
        return List.of(
                "gasto-por-categoria",
                "ingresos-gastos",
                "patrimonio",
                "cuentas/" + cuentaDeAna + "/evolucion-saldo",
                "metas");
    }

    /** En las metas {@code hasta} es opcional (por defecto, el mismo mes de {@code desde}). */
    private static boolean hastaEsObligatorio(String ruta) {
        return !ruta.equals("metas");
    }

    /** Los meses que cubre la respuesta: su lista, o el rango desde-hasta si no la trae. */
    private static int cantidadDeMeses(JsonNode cuerpo) {
        if (cuerpo.has("meses")) {
            return cuerpo.get("meses").size();
        }
        java.time.YearMonth desde = java.time.YearMonth.parse(cuerpo.get("desde").asString());
        java.time.YearMonth hasta = java.time.YearMonth.parse(cuerpo.get("hasta").asString());
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(desde, hasta) + 1;
    }

    @Test
    void sinTokenTodasLasRutasResponden401() throws Exception {
        for (String ruta : rutas()) {
            mockMvc.perform(get(ApoyoHttpReporte.RUTA_PRESUPUESTOS + "/" + ana.presupuestoId()
                            + "/reportes/" + ruta)
                            .param("desde", "2026-10").param("hasta", "2026-10"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    @Test
    void unPresupuestoAjenoOInexistenteEs404AunConParametrosInvalidos() throws Exception {
        Sesion beto = http.registrar("beto-rango@ejemplo.com");
        for (String ruta : rutas()) {
            // La cuenta de Ana tampoco existe en el presupuesto de Beto: 404 por presupuesto.
            ApoyoHttpReporte.esperarNoEncontrado(
                    http.reporte(ana, beto.presupuestoId(), ruta, "desde", "2026-13"));
            ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                    ana, PRESUPUESTO_INEXISTENTE, ruta, "desde", "2026-13"));
            ApoyoHttpReporte.esperarNoEncontrado(http.reporte(
                    ana, PRESUPUESTO_INEXISTENTE, ruta, "desde", "2026-10", "hasta", "2026-10"));
        }
    }

    @Test
    void unMesMalFormadoOUnParametroAusenteEs400DatosInvalidos() throws Exception {
        for (String ruta : rutas()) {
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "2026-13", "hasta", "2026-12"));
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "2026-1", "hasta", "2026-12"));
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "ayer", "hasta", "2026-12"));
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "2026-10", "hasta", "1999-12"));
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta, "hasta", "2026-12"));
            if (hastaEsObligatorio(ruta)) {
                ApoyoHttpReporte.esperarDatosInvalidos(
                        http.reporte(ana, ruta, "desde", "2026-10"));
            } else {
                http.reporte(ana, ruta, "desde", "2026-10").andExpect(status().isOk());
            }
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta));
        }
    }

    @Test
    void elMensajeDeUnMesInvalidoNoReflejaElValorRecibido() throws Exception {
        for (String ruta : rutas()) {
            JsonNode cuerpo = http.cuerpo(http.reporte(ana, ruta,
                    "desde", "valor-raro-xyz", "hasta", "2026-12")
                    .andExpect(status().isBadRequest()));

            assertThat(cuerpo.toString()).doesNotContain("valor-raro-xyz");
        }
    }

    @Test
    void unRangoInvertidoEs400() throws Exception {
        for (String ruta : rutas()) {
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "2026-10", "hasta", "2026-09"));
        }
    }

    @Test
    void sesentaMesesResponden200YSesentaYUnoResponden400() throws Exception {
        for (String ruta : rutas()) {
            JsonNode sesenta = http.cuerpo(http.reporte(ana, ruta,
                    "desde", "2022-01", "hasta", "2026-12").andExpect(status().isOk()));

            assertThat(cantidadDeMeses(sesenta)).as(ruta).isEqualTo(60);
            ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, ruta,
                    "desde", "2022-01", "hasta", "2027-01"));
        }
    }

    @Test
    void unSoloMesDaUnSoloElemento() throws Exception {
        for (String ruta : rutas()) {
            JsonNode cuerpo = http.reporteOk(ana, ruta, "desde", "2026-10", "hasta", "2026-10");

            assertThat(cantidadDeMeses(cuerpo)).as(ruta).isEqualTo(1);
        }
    }

    @Test
    void unPresupuestoSinDatosDevuelveTodosLosMesesEnCero() throws Exception {
        Sesion vacia = http.registrar("vacia-rango@ejemplo.com");
        for (String ruta : List.of("ingresos-gastos", "patrimonio")) {
            JsonNode meses = http.reporteOk(
                    vacia, ruta, "desde", "2026-01", "hasta", "2026-03").get("meses");

            assertThat(meses).as(ruta).hasSize(3);
            for (JsonNode mes : meses) {
                mes.properties().forEach(campo -> {
                    if (campo.getValue().isNumber()) {
                        assertThat(campo.getValue().asLong()).as(ruta + "." + campo.getKey())
                                .isZero();
                    }
                });
            }
        }
    }

    @Test
    void cadaPersonaVeSoloLasCifrasDeSuPropioPresupuesto() throws Exception {
        long comidaDeAna = http.crearCategoria(ana, "Comida");
        http.transaccion(ana, cuentaDeAna, "2026-10-05", 70_000L, null);
        http.transaccion(ana, cuentaDeAna, "2026-10-06", -20_000L, comidaDeAna);
        Sesion beto = http.registrar("beto-aislamiento@ejemplo.com");
        long cuentaDeBeto = http.crearCuenta(beto, "Corriente", "CORRIENTE", true, 900_000L);
        long comidaDeBeto = http.crearCategoria(beto, "Comida");
        http.transaccion(beto, cuentaDeBeto, "2026-10-05", 5_000L, null);
        http.transaccion(beto, cuentaDeBeto, "2026-10-06", -3_000L, comidaDeBeto);

        JsonNode mensualDeAna =
                http.reporteOk(ana, "ingresos-gastos", "desde", "2026-10", "hasta", "2026-10");
        JsonNode mensualDeBeto =
                http.reporteOk(beto, "ingresos-gastos", "desde", "2026-10", "hasta", "2026-10");
        JsonNode patrimonioDeAna =
                http.reporteOk(ana, "patrimonio", "desde", "2026-10", "hasta", "2026-10");
        JsonNode patrimonioDeBeto =
                http.reporteOk(beto, "patrimonio", "desde", "2026-10", "hasta", "2026-10");

        assertThat(mensualDeAna.get("ingresos").asLong()).isEqualTo(70_000L);
        assertThat(mensualDeAna.get("gastos").asLong()).isEqualTo(20_000L);
        assertThat(mensualDeBeto.get("ingresos").asLong()).isEqualTo(5_000L);
        assertThat(mensualDeBeto.get("gastos").asLong()).isEqualTo(3_000L);
        assertThat(patrimonioDeAna.get("meses").get(0).get("patrimonio").asLong())
                .isEqualTo(150_000L);
        assertThat(patrimonioDeBeto.get("meses").get(0).get("patrimonio").asLong())
                .isEqualTo(902_000L);
    }

    @Test
    void loQueNoEsGetResponde405YNoCambiaNada() throws Exception {
        for (String ruta : rutas()) {
            mockMvc.perform(post(ApoyoHttpReporte.RUTA_PRESUPUESTOS + "/" + ana.presupuestoId()
                            + "/reportes/" + ruta)
                            .header(HttpHeaders.AUTHORIZATION, ApoyoHttpReporte.bearer(ana.token()))
                            .param("desde", "2026-10").param("hasta", "2026-10"))
                    .andExpect(status().isMethodNotAllowed());
        }
        JsonNode saldos = http.reporteOk(
                ana, "cuentas/" + cuentaDeAna + "/evolucion-saldo", "desde", "2026-10",
                "hasta", "2026-10");
        assertThat(saldos.get("meses").get(0).get("saldo").asLong()).isEqualTo(100_000L);
    }
}
