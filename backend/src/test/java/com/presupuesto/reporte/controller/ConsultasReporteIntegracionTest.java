package com.presupuesto.reporte.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.reporte.controller.ApoyoHttpReporte.Sesion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.YearMonth;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
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
 * Cada reporte hace un número fijo de consultas, que no crece con los meses del rango (ni con
 * las categorías, las cuentas o las transacciones). Se cuentan con las estadísticas de Hibernate;
 * antes de cada petición se vacía el contexto de persistencia del test para que no oculte
 * consultas con entidades en caché. Las estadísticas se activan en ejecución y no con una
 * propiedad: otra configuración de contexto abriría otro pool de conexiones en la suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultasReporteIntegracionTest {

    private static final long CONSULTAS_GASTO_POR_CATEGORIA = 7L;
    private static final long CONSULTAS_INGRESOS_GASTOS = 5L;
    private static final long CONSULTAS_PATRIMONIO = 3L;
    private static final long CONSULTAS_EVOLUCION_SALDO = 3L;
    private static final long CONSULTAS_METAS_SIN_TARJETAS = 7L;
    private static final long CONSULTAS_METAS_CON_TARJETAS = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    private ApoyoHttpReporte http;
    private Sesion ana;
    private long cuenta;

    @BeforeEach
    void datosEnVeinticuatroMeses() throws Exception {
        estadisticas().setStatisticsEnabled(true);
        http = new ApoyoHttpReporte(mockMvc, objectMapper);
        ana = http.registrar("ana-consultas@ejemplo.com");
        cuenta = http.crearCuenta(ana, "Corriente", "CORRIENTE", true, 100_000L);
        long visa = http.crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -10_000L);
        long fuera = http.crearCuenta(ana, "Inversion", "INVERSION", false, 50_000L);
        long grupo = http.crearGrupo(ana, "Grupo de consultas");
        long comida = http.crearCategoria(ana, grupo, "Comida");
        long hogar = http.crearCategoria(ana, grupo, "Hogar");
        for (YearMonth mes = YearMonth.of(2025, 1); !mes.isAfter(YearMonth.of(2026, 12));
                mes = mes.plusMonths(1)) {
            http.transaccion(ana, cuenta, mes.atDay(2).toString(), 300_000L, null);
            http.transaccion(ana, cuenta, mes.atDay(3).toString(), -40_000L, comida);
            http.transaccion(ana, visa, mes.atDay(4).toString(), -9_000L, hogar);
            http.transaccion(ana, cuenta, mes.atDay(5).toString(), -1_000L, null);
            http.transaccion(ana, fuera, mes.atDay(6).toString(), 2_000L, null);
            http.division(ana, cuenta, mes.atDay(7).toString(), -6_000L, List.of(
                    new Long[] {comida, -4_000L}, new Long[] {null, -2_000L}));
        }
        entityManager.flush();
    }

    @AfterEach
    void desactivarEstadisticas() {
        estadisticas().setStatisticsEnabled(false);
    }

    private Statistics estadisticas() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** Consultas que ejecuta una petición de reporte con un contexto de persistencia vacío. */
    private long consultas(String ruta, String desde, String hasta) throws Exception {
        entityManager.flush();
        entityManager.clear();
        estadisticas().clear();
        http.reporte(ana, ruta, "desde", desde, "hasta", hasta).andExpect(status().isOk());
        return estadisticas().getPrepareStatementCount();
    }

    private void esperarConsultasFijas(String ruta, long esperadas) throws Exception {
        long unMes = consultas(ruta, "2026-10", "2026-10");
        long veinticuatro = consultas(ruta, "2025-01", "2026-12");

        assertThat(unMes).as("%s con 1 mes", ruta).isEqualTo(esperadas);
        assertThat(veinticuatro).as("%s con 24 meses", ruta).isEqualTo(esperadas);
    }

    @Test
    void elGastoPorCategoriaHaceSieteConsultasConUnoYConVeinticuatroMeses() throws Exception {
        esperarConsultasFijas("gasto-por-categoria", CONSULTAS_GASTO_POR_CATEGORIA);
    }

    @Test
    void ingresosContraGastosHaceCincoConsultasConUnoYConVeinticuatroMeses() throws Exception {
        esperarConsultasFijas("ingresos-gastos", CONSULTAS_INGRESOS_GASTOS);
    }

    @Test
    void elPatrimonioHaceTresConsultasConUnoYConVeinticuatroMeses() throws Exception {
        esperarConsultasFijas("patrimonio", CONSULTAS_PATRIMONIO);
    }

    @Test
    void laEvolucionDeSaldoHaceTresConsultasConUnoYConVeinticuatroMeses() throws Exception {
        esperarConsultasFijas("cuentas/" + cuenta + "/evolucion-saldo", CONSULTAS_EVOLUCION_SALDO);
    }

    @Test
    void lasMetasHacenDiezConsultasConTarjetasConUnoYConVeinticuatroMeses() throws Exception {
        long comida = http.crearCategoria(ana, "Comida de metas");
        http.guardarMeta(ana, comida, ApoyoHttpReporte.metaMensual(100_000L));

        esperarConsultasFijas("metas", CONSULTAS_METAS_CON_TARJETAS);
    }

    @Test
    void lasMetasSinTarjetasHacenSieteConsultasConUnoYConVeinticuatroMeses() throws Exception {
        Sesion sinTarjetas = http.registrar("sin-tarjetas-consultas@ejemplo.com");
        long corriente = http.crearCuenta(sinTarjetas, "Corriente", "CORRIENTE", true, 0L);
        long comida = http.crearCategoria(sinTarjetas, "Comida");
        long hogar = http.crearCategoria(sinTarjetas, "Hogar");
        http.guardarMeta(sinTarjetas, comida, ApoyoHttpReporte.metaMensual(100_000L));
        http.guardarMeta(sinTarjetas, hogar, ApoyoHttpReporte.saldoObjetivo(50_000L));
        http.transaccion(sinTarjetas, corriente, "2025-03-02", 900_000L, null);
        http.asignar(sinTarjetas, "2025-03", comida, 40_000L);
        http.transaccion(sinTarjetas, corriente, "2026-05-03", -10_000L, comida);
        ana = sinTarjetas;

        esperarConsultasFijas("metas", CONSULTAS_METAS_SIN_TARJETAS);
    }

    @Test
    void elNumeroDeConsultasNoDependeDeCuantosDatosHay() throws Exception {
        Sesion vacia = http.registrar("vacia-consultas@ejemplo.com");
        entityManager.flush();
        entityManager.clear();
        estadisticas().clear();

        http.reporte(vacia, "gasto-por-categoria", "desde", "2025-01", "hasta", "2026-12")
                .andExpect(status().isOk());

        assertThat(estadisticas().getPrepareStatementCount())
                .isEqualTo(CONSULTAS_GASTO_POR_CATEGORIA);
    }

    @Test
    void laFronteraDeMesSeRespetaConRangosDeUnoYDeVeinticuatroMeses() throws Exception {
        long corriente = http.crearCuenta(ana, "Frontera", "CORRIENTE", true, 0L);
        long categoria = http.crearCategoria(ana, "Frontera");
        http.transaccion(ana, corriente, "2027-02-28", -3_000L, categoria);
        http.transaccion(ana, corriente, "2027-03-01", -5_000L, categoria);

        JsonNode febrero = http.reporteOk(
                ana, "ingresos-gastos", "desde", "2027-02", "hasta", "2027-02");
        JsonNode largo = http.reporteOk(
                ana, "ingresos-gastos", "desde", "2025-04", "hasta", "2027-03");

        assertThat(febrero.get("meses").get(0).get("gastos").asLong()).isEqualTo(3_000L);
        assertThat(largo.get("meses")).hasSize(24);
        assertThat(largo.get("meses").get(22).get("mes").asString()).isEqualTo("2027-02");
        assertThat(largo.get("meses").get(22).get("gastos").asLong()).isEqualTo(3_000L);
        assertThat(largo.get("meses").get(23).get("gastos").asLong()).isEqualTo(5_000L);
    }
}
