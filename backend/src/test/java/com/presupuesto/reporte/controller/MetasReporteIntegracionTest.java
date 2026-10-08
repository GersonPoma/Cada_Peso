package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class MetasReporteIntegracionTest {

    private static final String METAS = "metas";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApoyoHttpReporte http;
    private Sesion ana;
    private long corriente;
    private long comida;

    @BeforeEach
    void preparar() throws Exception {
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-metas-reporte@ejemplo.com");
        corriente = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        comida = http.crearCategoria(ana, "Comida");
        http.transaccion(ana, corriente, "2026-01-01", 5_000_000L, null);
    }

    private JsonNode metas(String desde, String hasta) throws Exception {
        return http.reporteOk(ana, METAS, "desde", desde, "hasta", hasta);
    }

    private static JsonNode deCategoria(JsonNode respuesta, long categoriaId) {
        for (JsonNode meta : respuesta.get("metas")) {
            if (meta.get("categoriaId").asLong() == categoriaId) {
                return meta;
            }
        }
        throw new AssertionError("La categoría " + categoriaId + " no tiene meta en el reporte");
    }

    @Test
    void elEjemploDeUnMesDaNecesidadAsignadoGastadoFaltanteEstadoYPorcentaje() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.asignar(ana, "2026-10", comida, 80_000L);
        http.transaccion(ana, corriente, "2026-10-12", -60_000L, comida);

        JsonNode respuesta = http.reporteOk(ana, METAS, "desde", "2026-10");

        assertThat(respuesta.get("desde").asString()).isEqualTo("2026-10");
        assertThat(respuesta.get("hasta").asString()).isEqualTo("2026-10");
        JsonNode meta = deCategoria(respuesta, comida);
        assertThat(meta.get("nombre").asString()).isEqualTo("Comida");
        assertThat(meta.get("tipo").asString()).isEqualTo("MONTO_MENSUAL");
        assertThat(meta.get("monto").asLong()).isEqualTo(100_000L);
        assertThat(meta.get("meses")).hasSize(1);
        JsonNode mes = meta.get("meses").get(0);
        assertThat(mes.get("mes").asString()).isEqualTo("2026-10");
        assertThat(mes.get("necesidad").asLong()).isEqualTo(100_000L);
        assertThat(mes.get("asignado").asLong()).isEqualTo(80_000L);
        assertThat(mes.get("gastado").asLong()).isEqualTo(60_000L);
        assertThat(mes.get("disponible").asLong()).isEqualTo(20_000L);
        assertThat(mes.get("faltante").asLong()).isEqualTo(20_000L);
        assertThat(mes.get("estado").asString()).isEqualTo("FALTA");
        assertThat(mes.get("porcentaje").asLong()).isEqualTo(8_000L);
    }

    @Test
    void unRangoDeDosMesesDaLosTotalesDelRango() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.asignar(ana, "2026-09", comida, 100_000L);
        http.transaccion(ana, corriente, "2026-09-12", -60_000L, comida);
        http.asignar(ana, "2026-10", comida, 80_000L);

        JsonNode meta = deCategoria(metas("2026-09", "2026-10"), comida);

        assertThat(meta.get("meses")).hasSize(2);
        assertThat(meta.get("necesidad").asLong()).isEqualTo(200_000L);
        assertThat(meta.get("asignado").asLong()).isEqualTo(180_000L);
        assertThat(meta.get("gastado").asLong()).isEqualTo(60_000L);
        assertThat(meta.get("porcentaje").asLong()).isEqualTo(9_000L);
        assertThat(meta.get("meses").get(0).get("porcentaje").asLong()).isEqualTo(10_000L);
        assertThat(meta.get("meses").get(1).get("porcentaje").asLong()).isEqualTo(8_000L);
    }

    @Test
    void unaMetaSeEvaluaTambienEnMesesAnterioresASuCreacion() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.asignar(ana, "2026-02", comida, 40_000L);

        JsonNode meta = deCategoria(metas("2026-01", "2026-03"), comida);

        assertThat(meta.get("meses")).hasSize(3);
        for (JsonNode mes : meta.get("meses")) {
            assertThat(mes.get("necesidad").asLong()).isEqualTo(100_000L);
        }
        assertThat(meta.get("meses").get(0).get("asignado").asLong()).isZero();
        assertThat(meta.get("meses").get(1).get("asignado").asLong()).isEqualTo(40_000L);
        assertThat(meta.get("meses").get(1).get("porcentaje").asLong()).isEqualTo(4_000L);
        assertThat(meta.get("necesidad").asLong()).isEqualTo(300_000L);
    }

    @Test
    void losTresTiposDeMetaSeCalculanComoEnElEstadoDeMetasDelMes() throws Exception {
        long ahorro = http.crearCategoria(ana, "Ahorro");
        long viaje = http.crearCategoria(ana, "Viaje");
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.guardarMeta(ana, ahorro, ApoyoHttpReporte.saldoObjetivo(50_000L));
        http.guardarMeta(ana, viaje, ApoyoHttpReporte.paraFecha(120_000L, "2026-12-01"));
        http.asignar(ana, "2026-10", comida, 100_000L);
        http.asignar(ana, "2026-10", ahorro, 20_000L);
        http.asignar(ana, "2026-10", viaje, 10_000L);

        JsonNode respuesta = metas("2026-10", "2026-10");

        assertThat(respuesta.get("metas")).hasSize(3);
        JsonNode mensual = deCategoria(respuesta, comida).get("meses").get(0);
        assertThat(mensual.get("estado").asString()).isEqualTo("FINANCIADA");
        assertThat(mensual.get("faltante").asLong()).isZero();
        JsonNode objetivo = deCategoria(respuesta, ahorro).get("meses").get(0);
        assertThat(objetivo.get("necesidad").asLong()).isEqualTo(50_000L);
        assertThat(objetivo.get("faltante").asLong()).isEqualTo(30_000L);
        JsonNode paraFecha = deCategoria(respuesta, viaje).get("meses").get(0);
        // 120000 en los 3 meses que quedan de octubre a diciembre: 40000 por mes
        assertThat(paraFecha.get("necesidad").asLong()).isEqualTo(40_000L);
        assertThat(paraFecha.get("faltante").asLong()).isEqualTo(30_000L);
        // el orden es el del árbol de categorías: Comida, Ahorro, Viaje
        assertThat(respuesta.get("metas").get(0).get("categoriaId").asLong()).isEqualTo(comida);
        assertThat(respuesta.get("metas").get(2).get("categoriaId").asLong()).isEqualTo(viaje);
    }

    @Test
    void unaMetaPospuestaTieneNecesidadCeroPorcentajeNuloYEstadoPospuesta() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.posponerMeta(ana, "2026-10", comida);

        JsonNode meta = deCategoria(metas("2026-09", "2026-10"), comida);

        JsonNode septiembre = meta.get("meses").get(0);
        JsonNode octubre = meta.get("meses").get(1);
        assertThat(septiembre.get("necesidad").asLong()).isEqualTo(100_000L);
        assertThat(octubre.get("necesidad").asLong()).isZero();
        assertThat(octubre.get("faltante").asLong()).isZero();
        assertThat(octubre.get("estado").asString()).isEqualTo("POSPUESTA");
        assertThat(octubre.get("porcentaje").isNull()).isTrue();
        assertThat(meta.get("necesidad").asLong()).isEqualTo(100_000L);
    }

    @Test
    void unaCategoriaSobregastadaTieneEstadoSobregastada() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.asignar(ana, "2026-10", comida, 30_000L);
        http.transaccion(ana, corriente, "2026-10-12", -50_000L, comida);

        JsonNode mes = deCategoria(metas("2026-10", "2026-10"), comida).get("meses").get(0);

        assertThat(mes.get("estado").asString()).isEqualTo("SOBREGASTADA");
        assertThat(mes.get("gastado").asLong()).isEqualTo(50_000L);
        assertThat(mes.get("disponible").asLong()).isEqualTo(-20_000L);
    }

    @Test
    void enLaCategoriaDePagoDeUnaTarjetaLoGastadoEsCeroAunqueHayaReserva() throws Exception {
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0L);
        long pagoVisa = http.categoriaDePago(ana, visa);
        http.guardarMeta(ana, pagoVisa, ApoyoHttpReporte.metaMensual(40_000L));
        http.transaccion(ana, visa, "2026-10-12", -30_000L, comida);
        http.asignar(ana, "2026-10", pagoVisa, 10_000L);

        JsonNode mes = deCategoria(metas("2026-10", "2026-10"), pagoVisa).get("meses").get(0);

        assertThat(mes.get("gastado").asLong()).isZero();
        assertThat(mes.get("asignado").asLong()).isEqualTo(10_000L);
        // la reserva de 30000 por el gasto con la tarjeta más lo asignado
        assertThat(mes.get("disponible").asLong()).isEqualTo(40_000L);
    }

    @Test
    void unaCategoriaOcultaConMetaSigueEnElReporte() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.ocultarCategoria(ana, comida);

        JsonNode meta = deCategoria(metas("2026-10", "2026-10"), comida);

        assertThat(meta.get("oculta").asBoolean()).isTrue();
    }

    @Test
    void sinMetasLaListaVieneVacia() throws Exception {
        JsonNode respuesta = metas("2026-10", "2026-10");

        assertThat(respuesta.get("metas")).isEmpty();
    }

    @Test
    void sinHastaElReporteTraeSoloElMesDeDesde() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));

        JsonNode respuesta = http.reporteOk(ana, METAS, "desde", "2026-10");

        assertThat(deCategoria(respuesta, comida).get("meses")).hasSize(1);
    }

    @Test
    void elPresupuestoAjenoEs404YSinDesdeEs400() throws Exception {
        Sesion beto = http.registrar("beto-metas-reporte@ejemplo.com");

        ApoyoHttpReporte.esperarNoEncontrado(
                http.reporte(ana, beto.presupuestoId(), METAS, "desde", "2026-13"));
        ApoyoHttpReporte.esperarNoEncontrado(
                http.reporte(ana, beto.presupuestoId(), METAS, "desde", "2026-10"));
        ApoyoHttpReporte.esperarDatosInvalidos(http.reporte(ana, METAS));
        ApoyoHttpReporte.esperarDatosInvalidos(
                http.reporte(ana, METAS, "desde", "2026-10", "hasta", "2026-09"));
        http.reporte(ana, METAS, "desde", "2026-10").andExpect(status().isOk());
    }

    @Test
    void lasMetasDeOtraPersonaNoAparecen() throws Exception {
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));
        Sesion beto = http.registrar("beto-metas-aisladas@ejemplo.com");

        JsonNode deBeto = http.cuerpo(http.reporte(beto, METAS, "desde", "2026-10")
                .andExpect(status().isOk()));

        assertThat(deBeto.get("metas")).isEmpty();
    }
}
