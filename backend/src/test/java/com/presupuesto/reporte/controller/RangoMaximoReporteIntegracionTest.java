package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * El rango máximo es configurable: aquí con {@code reportes.max-meses=12}. Cierra su contexto al
 * terminar para liberar su pool de conexiones: la suite ya tiene muchos contextos abiertos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "reportes.max-meses=12")
class RangoMaximoReporteIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void doceMesesResponden200YTreceResponden400() throws Exception {
        ApoyoHttpReporte http = new ApoyoHttpReporte(mockMvc, objectMapper);
        Sesion ana = http.registrar("ana-maximo@ejemplo.com");
        long cuenta = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 0L);
        List<String> rutas = List.of(
                "gasto-por-categoria",
                "ingresos-gastos",
                "patrimonio",
                "cuentas/" + cuenta + "/evolucion-saldo",
                "metas");

        for (String ruta : rutas) {
            http.reporte(ana, ruta, "desde", "2026-01", "hasta", "2026-12")
                    .andExpect(status().isOk());
            ApoyoHttpReporte.esperarDatosInvalidos(
                    http.reporte(ana, ruta, "desde", "2026-01", "hasta", "2027-01"));
        }
        assertThat(http.reporteOk(ana, "patrimonio", "desde", "2026-01", "hasta", "2026-12")
                .get("meses")).hasSize(12);
    }
}
