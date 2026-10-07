package com.presupuesto.meta.controller;

import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarDatosInvalidos;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarDatosInvalidosSinCampos;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarNoAutenticado;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarNoEncontrado;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.metaMensual;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.rutaMes;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.rutaMeta;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.saldoObjetivo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.meta.controller.ApoyoHttpMeta.Sesion;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetaMesIntegracionTest {

    private static final String OCTUBRE = "2026-10";
    private static final String NOVIEMBRE = "2026-11";
    private static final String SEPTIEMBRE = "2026-09";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MetaRepository metaRepository;

    @Autowired
    private MetaPospuestaRepository pospuestaRepository;

    private ApoyoHttpMeta http;

    @BeforeEach
    void preparar() {
        http = new ApoyoHttpMeta(mockMvc, objectMapper);
    }

    // ---------- ayudas ----------

    private void meta(Sesion sesion, long categoriaId, Map<String, Object> cuerpo)
            throws Exception {
        http.enviar(put(rutaMeta(sesion.presupuestoId(), categoriaId)), sesion, cuerpo)
                .andExpect(status().isOk());
    }

    private static Map<String, Object> semanal(long monto, int diaSemana) {
        Map<String, Object> cuerpo = metaMensual(monto);
        cuerpo.put("frecuencia", "SEMANAL");
        cuerpo.put("diaSemana", diaSemana);
        return cuerpo;
    }

    private static Map<String, Object> personalizada(long monto, int dias, String inicio) {
        Map<String, Object> cuerpo = metaMensual(monto);
        cuerpo.put("frecuencia", "PERSONALIZADA");
        cuerpo.put("intervaloDias", dias);
        cuerpo.put("fechaInicio", inicio);
        return cuerpo;
    }

    private static Map<String, Object> paraFecha(long monto, String objetivo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "MONTO_PARA_FECHA");
        cuerpo.put("monto", monto);
        cuerpo.put("fechaObjetivo", objetivo);
        return cuerpo;
    }

    private JsonNode estadoDelMes(Sesion sesion, String mes, boolean incluirOcultas)
            throws Exception {
        return http.cuerpo(http.consultar(sesion, rutaMes(sesion.presupuestoId(), mes) + "/metas",
                "incluirOcultas", String.valueOf(incluirOcultas)).andExpect(status().isOk()));
    }

    private JsonNode elemento(Sesion sesion, String mes, long categoriaId) throws Exception {
        for (JsonNode meta : estadoDelMes(sesion, mes, true).get("metas")) {
            if (meta.get("categoriaId").asLong() == categoriaId) {
                return meta;
            }
        }
        throw new AssertionError("La categoría " + categoriaId + " no tiene meta en " + mes);
    }

    private void esperarElemento(
            Sesion sesion, String mes, long categoriaId, long necesidad, long faltante,
            String estado) throws Exception {
        JsonNode elemento = elemento(sesion, mes, categoriaId);
        assertThat(elemento.get("necesidad").asLong()).as("necesidad").isEqualTo(necesidad);
        assertThat(elemento.get("faltante").asLong()).as("faltante").isEqualTo(faltante);
        assertThat(elemento.get("estado").asString()).as("estado").isEqualTo(estado);
    }

    private ResultActions autoAsignar(
            Sesion sesion, String mes, String estrategia, List<Long> categoriaIds,
            Boolean simular) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (estrategia != null) {
            cuerpo.put("estrategia", estrategia);
        }
        if (categoriaIds != null) {
            cuerpo.put("categoriaIds", categoriaIds);
        }
        if (simular != null) {
            cuerpo.put("simular", simular);
        }
        return http.enviar(post(rutaMes(sesion.presupuestoId(), mes) + "/auto-asignar"),
                sesion, cuerpo);
    }

    private ResultActions posponer(Sesion sesion, String mes, long categoriaId) throws Exception {
        return http.accion(sesion,
                rutaMes(sesion.presupuestoId(), mes) + "/metas/" + categoriaId + "/posponer");
    }

    private ResultActions reanudar(Sesion sesion, String mes, long categoriaId) throws Exception {
        return http.accion(sesion,
                rutaMes(sesion.presupuestoId(), mes) + "/metas/" + categoriaId + "/reanudar");
    }

    private long asignado(Sesion sesion, String mes, long categoriaId) throws Exception {
        return http.asignadoDe(sesion, sesion.presupuestoId(), mes, categoriaId);
    }

    // ---------- autenticación y aislamiento ----------

    @Test
    void sinTokenTodasLasRutasDeUnMesDevuelven401() throws Exception {
        String ruta = rutaMes(1, OCTUBRE);

        esperarNoAutenticado(mockMvc.perform(get(ruta + "/metas")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/metas/1/posponer")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/metas/1/reanudar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/auto-asignar")
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    @Test
    void unPresupuestoAjenoResponde404AunqueElMesSeaInvalido() throws Exception {
        Sesion ana = http.registrar("ana-mes1@ejemplo.com");
        Sesion beto = http.registrar("beto-mes1@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        meta(ana, comida, metaMensual(100_000L));
        http.asignar(ana, ana.presupuestoId(), OCTUBRE, comida, 30_000L);

        for (String mes : List.of(OCTUBRE, "2026-13")) {
            String ruta = rutaMes(ana.presupuestoId(), mes);
            esperarNoEncontrado(http.consultar(beto, ruta + "/metas"));
            esperarNoEncontrado(posponerEn(beto, ruta, comida, "posponer"));
            esperarNoEncontrado(posponerEn(beto, ruta, comida, "reanudar"));
            esperarNoEncontrado(http.enviar(post(ruta + "/auto-asignar"), beto,
                    Map.of("estrategia", "FALTANTE_META")));
        }
        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(30_000L);
        assertThat(pospuestaRepository.countByMetaId(
                metaRepository.findByCategoriaId(comida).orElseThrow().getId())).isZero();
    }

    private ResultActions posponerEn(Sesion sesion, String rutaMes, long categoriaId,
            String accion) throws Exception {
        return http.accion(sesion, rutaMes + "/metas/" + categoriaId + "/" + accion);
    }

    @Test
    void laCategoriaAjenaSinMetaOInexistenteResponde404AlPosponerYReanudar() throws Exception {
        Sesion ana = http.registrar("ana-mes2@ejemplo.com");
        Sesion beto = http.registrar("beto-mes2@ejemplo.com");
        long segundo = http.crearPresupuesto(ana, "Segundo");
        long deAna = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        long sinMeta = http.crearCategoria(ana, ana.presupuestoId(), "Sin meta");
        meta(ana, deAna, metaMensual(100_000L));

        // Categoría de otra persona y de otro presupuesto de la misma persona.
        esperarNoEncontrado(http.accion(beto, rutaMes(beto.presupuestoId(), OCTUBRE)
                + "/metas/" + deAna + "/posponer"));
        esperarNoEncontrado(http.accion(ana, rutaMes(segundo, OCTUBRE)
                + "/metas/" + deAna + "/reanudar"));
        // Sin meta e inexistente.
        esperarNoEncontrado(posponer(ana, OCTUBRE, sinMeta));
        esperarNoEncontrado(reanudar(ana, OCTUBRE, sinMeta));
        esperarNoEncontrado(posponer(ana, OCTUBRE, Long.MAX_VALUE));
        assertThat(pospuestaRepository.countByMetaId(
                metaRepository.findByCategoriaId(deAna).orElseThrow().getId())).isZero();
    }

    @Test
    void unMesInvalidoDevuelve400DatosInvalidosEnLasCuatroRutas() throws Exception {
        Sesion ana = http.registrar("ana-mes3@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        meta(ana, comida, metaMensual(100_000L));

        for (String mes : List.of("2026-13", "2026-1", "2026-00", "enero", "1999-12", "2101-01")) {
            String ruta = rutaMes(ana.presupuestoId(), mes);
            esperarDatosInvalidosSinCampos(http.consultar(ana, ruta + "/metas"));
            esperarDatosInvalidosSinCampos(posponer(ana, mes, comida));
            esperarDatosInvalidosSinCampos(reanudar(ana, mes, comida));
            esperarDatosInvalidosSinCampos(autoAsignar(ana, mes, "FALTANTE_META", null, false));
        }
        assertThat(asignado(ana, OCTUBRE, comida)).isZero();
    }

    // ---------- ejemplos A a G ----------

    @Test
    void ejemploAMontoMensual() throws Exception {
        Sesion ana = http.registrar("ana-mes4@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        meta(ana, comida, metaMensual(100_000L));

        http.asignar(ana, ana.presupuestoId(), OCTUBRE, comida, 60_000L);
        esperarElemento(ana, OCTUBRE, comida, 100_000L, 40_000L, "FALTA");

        http.asignar(ana, ana.presupuestoId(), OCTUBRE, comida, 100_000L);
        esperarElemento(ana, OCTUBRE, comida, 100_000L, 0L, "FINANCIADA");
        JsonNode elemento = elemento(ana, OCTUBRE, comida);
        assertThat(elemento.get("nombre").asString()).isEqualTo("Comida");
        assertThat(elemento.get("tipo").asString()).isEqualTo("MONTO_MENSUAL");
        assertThat(elemento.get("monto").asLong()).isEqualTo(100_000L);
        assertThat(elemento.get("asignado").asLong()).isEqualTo(100_000L);
        assertThat(elemento.get("disponible").asLong()).isEqualTo(100_000L);
    }

    @Test
    void ejemploBSemanalLosLunes() throws Exception {
        Sesion ana = http.registrar("ana-mes5@ejemplo.com");
        long gimnasio = http.crearCategoria(ana, ana.presupuestoId(), "Gimnasio");
        meta(ana, gimnasio, semanal(20_000L, 1));

        esperarElemento(ana, OCTUBRE, gimnasio, 80_000L, 80_000L, "FALTA");
        esperarElemento(ana, NOVIEMBRE, gimnasio, 100_000L, 100_000L, "FALTA");
    }

    @Test
    void ejemploCPersonalizadaYC2IntervaloLargo() throws Exception {
        Sesion ana = http.registrar("ana-mes6@ejemplo.com");
        long cada14 = http.crearCategoria(ana, ana.presupuestoId(), "Cada 14");
        long cada30 = http.crearCategoria(ana, ana.presupuestoId(), "Cada 30");
        meta(ana, cada14, personalizada(10_000L, 14, "2026-10-02"));
        meta(ana, cada30, personalizada(10_000L, 30, "2026-10-02"));

        esperarElemento(ana, SEPTIEMBRE, cada14, 0L, 0L, "FINANCIADA");
        esperarElemento(ana, OCTUBRE, cada14, 30_000L, 30_000L, "FALTA");
        esperarElemento(ana, NOVIEMBRE, cada14, 20_000L, 20_000L, "FALTA");
        esperarElemento(ana, "2027-02", cada30, 0L, 0L, "FINANCIADA");
        esperarElemento(ana, "2026-12", cada30, 20_000L, 20_000L, "FALTA");
    }

    @Test
    void ejemploDMontoParaFecha() throws Exception {
        Sesion ana = http.registrar("ana-mes7@ejemplo.com");
        long p = ana.presupuestoId();
        long normal = http.crearCategoria(ana, p, "Normal");
        long conInicial = http.crearCategoria(ana, p, "Con inicial");
        long redondeo = http.crearCategoria(ana, p, "Redondeo");
        long ultimoMes = http.crearCategoria(ana, p, "Ultimo mes");
        meta(ana, normal, paraFecha(600_000L, "2026-12-15"));
        meta(ana, conInicial, paraFecha(600_000L, "2026-12-15"));
        meta(ana, redondeo, paraFecha(100_000L, "2026-12-15"));
        meta(ana, ultimoMes, paraFecha(600_000L, "2026-12-15"));
        http.asignar(ana, p, SEPTIEMBRE, conInicial, 150_000L);
        http.asignar(ana, p, "2026-12", ultimoMes, 450_000L);

        esperarElemento(ana, OCTUBRE, normal, 200_000L, 200_000L, "FALTA");
        esperarElemento(ana, OCTUBRE, conInicial, 150_000L, 150_000L, "FALTA");
        esperarElemento(ana, OCTUBRE, redondeo, 33_334L, 33_334L, "FALTA");
        esperarElemento(ana, "2027-01", ultimoMes, 150_000L, 150_000L, "FALTA");
    }

    @Test
    void ejemploESaldoObjetivo() throws Exception {
        Sesion ana = http.registrar("ana-mes8@ejemplo.com");
        long p = ana.presupuestoId();
        long parcial = http.crearCategoria(ana, p, "Parcial");
        long superado = http.crearCategoria(ana, p, "Superado");
        meta(ana, parcial, saldoObjetivo(300_000L));
        meta(ana, superado, saldoObjetivo(300_000L));
        http.asignar(ana, p, SEPTIEMBRE, parcial, 120_000L);
        http.asignar(ana, p, SEPTIEMBRE, superado, 400_000L);

        esperarElemento(ana, OCTUBRE, parcial, 180_000L, 180_000L, "FALTA");
        esperarElemento(ana, OCTUBRE, superado, 0L, 0L, "FINANCIADA");
        http.asignar(ana, p, OCTUBRE, parcial, 180_000L);
        esperarElemento(ana, OCTUBRE, parcial, 180_000L, 0L, "FINANCIADA");
    }

    @Test
    void ejemploFSobregastadaGanaAunqueEsteFinanciadaOPospuesta() throws Exception {
        Sesion ana = http.registrar("ana-mes9@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = http.crearCuenta(ana, p, 500_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        meta(ana, comida, metaMensual(100_000L));
        http.asignar(ana, p, OCTUBRE, comida, 100_000L);
        http.crearTransaccion(ana, p, cuenta, "2026-10-05", -105_000L, comida);

        esperarElemento(ana, OCTUBRE, comida, 100_000L, 0L, "SOBREGASTADA");
        assertThat(elemento(ana, OCTUBRE, comida).get("disponible").asLong()).isEqualTo(-5_000L);
        posponer(ana, OCTUBRE, comida).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SOBREGASTADA"));
        esperarElemento(ana, OCTUBRE, comida, 0L, 0L, "SOBREGASTADA");
    }

    @Test
    void ejemploGPosponerSoloAfectaAEseMesYReanudarLoDevuelveAlCalculo() throws Exception {
        Sesion ana = http.registrar("ana-mes10@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        meta(ana, comida, metaMensual(100_000L));

        posponer(ana, OCTUBRE, comida)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(comida))
                .andExpect(jsonPath("$.estado").value("POSPUESTA"))
                .andExpect(jsonPath("$.necesidad").value(0))
                .andExpect(jsonPath("$.faltante").value(0));

        esperarElemento(ana, OCTUBRE, comida, 0L, 0L, "POSPUESTA");
        esperarElemento(ana, NOVIEMBRE, comida, 100_000L, 100_000L, "FALTA");
        esperarElemento(ana, SEPTIEMBRE, comida, 100_000L, 100_000L, "FALTA");

        reanudar(ana, OCTUBRE, comida)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALTA"))
                .andExpect(jsonPath("$.necesidad").value(100_000));
        esperarElemento(ana, OCTUBRE, comida, 100_000L, 100_000L, "FALTA");
    }

    @Test
    void posponerYReanudarSonIdempotentes() throws Exception {
        Sesion ana = http.registrar("ana-mes11@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        meta(ana, comida, metaMensual(100_000L));
        Long metaId = metaRepository.findByCategoriaId(comida).orElseThrow().getId();

        reanudar(ana, OCTUBRE, comida).andExpect(status().isOk());
        posponer(ana, OCTUBRE, comida).andExpect(status().isOk());
        posponer(ana, OCTUBRE, comida).andExpect(status().isOk());
        assertThat(pospuestaRepository.countByMetaId(metaId)).isEqualTo(1);

        reanudar(ana, OCTUBRE, comida).andExpect(status().isOk());
        reanudar(ana, OCTUBRE, comida).andExpect(status().isOk());
        assertThat(pospuestaRepository.countByMetaId(metaId)).isZero();
    }

    // ---------- estado del mes ----------

    @Test
    void incluirOcultasYElTotalFaltante() throws Exception {
        Sesion ana = http.registrar("ana-mes12@ejemplo.com");
        long p = ana.presupuestoId();
        long visible = http.crearCategoria(ana, p, "Visible");
        long oculta = http.crearCategoria(ana, p, "Oculta");
        meta(ana, visible, metaMensual(100_000L));
        meta(ana, oculta, metaMensual(10_000L));
        http.asignar(ana, p, OCTUBRE, visible, 60_000L);
        http.ocultarCategoria(ana, p, oculta);

        JsonNode sin = http.cuerpo(http.consultar(ana, rutaMes(p, OCTUBRE) + "/metas")
                .andExpect(status().isOk()));
        JsonNode con = estadoDelMes(ana, OCTUBRE, true);

        assertThat(sin.get("mes").asString()).isEqualTo(OCTUBRE);
        assertThat(sin.get("metas").size()).isEqualTo(1);
        assertThat(sin.get("totalFaltante").asLong()).isEqualTo(40_000L);
        assertThat(con.get("metas").size()).isEqualTo(2);
        assertThat(con.get("totalFaltante").asLong()).isEqualTo(50_000L);
        assertThat(con.get("metas").get(0).get("categoriaId").asLong()).isEqualTo(visible);
        assertThat(con.get("metas").get(1).get("categoriaId").asLong()).isEqualTo(oculta);
    }

    @Test
    void unPresupuestoSinMetasDaUnaListaVaciaYTotalCero() throws Exception {
        Sesion ana = http.registrar("ana-mes13@ejemplo.com");

        http.consultar(ana, rutaMes(ana.presupuestoId(), OCTUBRE) + "/metas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metas.length()").value(0))
                .andExpect(jsonPath("$.totalFaltante").value(0));
    }

    // ---------- auto-asignar ----------

    @Test
    void faltanteMetaDejaElAsignadoEnLaMetaYAjustaElListoParaAsignar() throws Exception {
        Sesion ana = http.registrar("ana-mes14@ejemplo.com");
        long p = ana.presupuestoId();
        http.crearCuenta(ana, p, 500_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        long ocio = http.crearCategoria(ana, p, "Ocio");
        meta(ana, comida, metaMensual(100_000L));
        http.asignar(ana, p, OCTUBRE, comida, 30_000L);
        http.asignar(ana, p, OCTUBRE, ocio, 5_000L);
        long listoAntes = http.listoParaAsignar(ana, p, OCTUBRE);

        autoAsignar(ana, OCTUBRE, "FALTANTE_META", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.listoParaAsignarAntes").value(listoAntes))
                .andExpect(jsonPath("$.listoParaAsignarDespues").value(listoAntes - 70_000L))
                .andExpect(jsonPath("$.cambios.length()").value(1))
                .andExpect(jsonPath("$.cambios[0].categoriaId").value(comida))
                .andExpect(jsonPath("$.cambios[0].nombre").value("Comida"))
                .andExpect(jsonPath("$.cambios[0].asignadoAntes").value(30_000))
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(100_000));

        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(100_000L);
        assertThat(asignado(ana, OCTUBRE, ocio)).isEqualTo(5_000L);
        assertThat(http.listoParaAsignar(ana, p, OCTUBRE)).isEqualTo(listoAntes - 70_000L);
        esperarElemento(ana, OCTUBRE, comida, 100_000L, 0L, "FINANCIADA");
    }

    @Test
    void asignadoMesPasadoCopiaLoDeSeptiembre() throws Exception {
        Sesion ana = http.registrar("ana-mes15@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios[0].asignadoAntes").value(0))
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(50_000));

        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(50_000L);
        assertThat(asignado(ana, SEPTIEMBRE, comida)).isEqualTo(50_000L);
    }

    @Test
    void gastadoMesPasadoUsaLoGastadoYUnIngresoDejaCero() throws Exception {
        Sesion ana = http.registrar("ana-mes16@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = http.crearCuenta(ana, p, 1_000_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        long reembolso = http.crearCategoria(ana, p, "Reembolso");
        http.crearTransaccion(ana, p, cuenta, "2026-09-10", -35_000L, comida);
        http.crearTransaccion(ana, p, cuenta, "2026-09-12", 4_000L, reembolso);
        http.asignar(ana, p, OCTUBRE, reembolso, 7_000L);

        autoAsignar(ana, OCTUBRE, "GASTADO_MES_PASADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios.length()").value(2));

        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(35_000L);
        assertThat(asignado(ana, OCTUBRE, reembolso)).isZero();
    }

    @Test
    void promedioAsignadoDeLosTresMesesAnteriores() throws Exception {
        Sesion ana = http.registrar("ana-mes17@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, "2026-07", comida, 30_000L);
        http.asignar(ana, p, "2026-08", comida, 60_000L);

        autoAsignar(ana, OCTUBRE, "PROMEDIO_ASIGNADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(30_000));

        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(30_000L);
    }

    @Test
    void promedioGastadoRedondeaHaciaAbajo() throws Exception {
        Sesion ana = http.registrar("ana-mes18@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = http.crearCuenta(ana, p, 1_000_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        http.crearTransaccion(ana, p, cuenta, "2026-07-10", -10_000L, comida);
        http.crearTransaccion(ana, p, cuenta, "2026-08-10", -10_000L, comida);
        http.crearTransaccion(ana, p, cuenta, "2026-09-10", -11L, comida);

        autoAsignar(ana, OCTUBRE, "PROMEDIO_GASTADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(6_670));

        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(6_670L);
    }

    @Test
    void simularNoGuardaNadaYDevuelveElMismoResultado() throws Exception {
        Sesion ana = http.registrar("ana-mes19@ejemplo.com");
        long p = ana.presupuestoId();
        http.crearCuenta(ana, p, 500_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        long ocio = http.crearCategoria(ana, p, "Ocio");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);
        http.asignar(ana, p, SEPTIEMBRE, ocio, 20_000L);
        long listoAntes = http.listoParaAsignar(ana, p, OCTUBRE);

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", null, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.cambios.length()").value(2))
                .andExpect(jsonPath("$.listoParaAsignarAntes").value(listoAntes))
                .andExpect(jsonPath("$.listoParaAsignarDespues").value(listoAntes - 70_000L));

        assertThat(asignado(ana, OCTUBRE, comida)).isZero();
        assertThat(asignado(ana, OCTUBRE, ocio)).isZero();
        assertThat(http.listoParaAsignar(ana, p, OCTUBRE)).isEqualTo(listoAntes);
    }

    @Test
    void conCategoriaIdsSeProcesaUnaOcultaYSoloLasListadas() throws Exception {
        Sesion ana = http.registrar("ana-mes20@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        long oculta = http.crearCategoria(ana, p, "Oculta");
        http.asignar(ana, p, SEPTIEMBRE, comida, 1_000L);
        http.asignar(ana, p, SEPTIEMBRE, oculta, 9_000L);
        http.ocultarCategoria(ana, p, oculta);

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", null, false)
                .andExpect(jsonPath("$.cambios.length()").value(1))
                .andExpect(jsonPath("$.cambios[0].categoriaId").value(comida));
        assertThat(asignado(ana, OCTUBRE, oculta)).isZero();

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", List.of(oculta), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios.length()").value(1))
                .andExpect(jsonPath("$.cambios[0].categoriaId").value(oculta))
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(9_000));
        assertThat(asignado(ana, OCTUBRE, oculta)).isEqualTo(9_000L);
    }

    @Test
    void unaCategoriaAjenaEnLaListaResponde404SinAplicarNada() throws Exception {
        Sesion ana = http.registrar("ana-mes21@ejemplo.com");
        Sesion beto = http.registrar("beto-mes21@ejemplo.com");
        long p = ana.presupuestoId();
        long segundo = http.crearPresupuesto(ana, "Segundo");
        long comida = http.crearCategoria(ana, p, "Comida");
        long deBeto = http.crearCategoria(beto, beto.presupuestoId(), "De Beto");
        long deOtroPresupuesto = http.crearCategoria(ana, segundo, "Otro");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);

        for (long ajena : new long[] {deBeto, deOtroPresupuesto, Long.MAX_VALUE}) {
            esperarNoEncontrado(autoAsignar(
                    ana, OCTUBRE, "ASIGNADO_MES_PASADO", List.of(comida, ajena), false));
        }

        assertThat(asignado(ana, OCTUBRE, comida)).isZero();
    }

    @Test
    void sinCambiosLaListaVaVaciaYLosDosListoSonIguales() throws Exception {
        Sesion ana = http.registrar("ana-mes22@ejemplo.com");
        long p = ana.presupuestoId();
        http.crearCuenta(ana, p, 500_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);
        http.asignar(ana, p, OCTUBRE, comida, 50_000L);
        long listo = http.listoParaAsignar(ana, p, OCTUBRE);

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.cambios.length()").value(0))
                .andExpect(jsonPath("$.listoParaAsignarAntes").value(listo))
                .andExpect(jsonPath("$.listoParaAsignarDespues").value(listo));
    }

    @Test
    void elListoParaAsignarPuedeQuedarNegativo() throws Exception {
        Sesion ana = http.registrar("ana-mes23@ejemplo.com");
        long p = ana.presupuestoId();
        http.crearCuenta(ana, p, 10_000L);
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, SEPTIEMBRE, comida, 80_000L);

        autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listoParaAsignarDespues").isNumber());

        assertThat(http.listoParaAsignar(ana, p, OCTUBRE)).isNegative();
        assertThat(asignado(ana, OCTUBRE, comida)).isEqualTo(80_000L);
    }

    @Test
    void unaEstrategiaAusenteODesconocidaDevuelve400YNoCambiaNada() throws Exception {
        Sesion ana = http.registrar("ana-mes24@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);

        esperarDatosInvalidos(autoAsignar(ana, OCTUBRE, null, null, false), "estrategia");
        esperarDatosInvalidosSinCampos(autoAsignar(ana, OCTUBRE, "INVENTADA", null, false));

        assertThat(asignado(ana, OCTUBRE, comida)).isZero();
    }

    @Test
    void unaListaDeCategoriasVaciaDevuelve400YNoCambiaNada() throws Exception {
        Sesion ana = http.registrar("ana-mes25@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, SEPTIEMBRE, comida, 50_000L);

        for (boolean simular : new boolean[] {false, true}) {
            esperarDatosInvalidos(
                    autoAsignar(ana, OCTUBRE, "ASIGNADO_MES_PASADO", List.of(), simular),
                    "categoriaIds");
        }

        assertThat(asignado(ana, OCTUBRE, comida)).isZero();
    }

    @Test
    void elPrimerMesDelRangoCuentaCeroEnLosMesesAnterioresFueraDeRango() throws Exception {
        Sesion ana = http.registrar("ana-mes26@ejemplo.com");
        long p = ana.presupuestoId();
        long comida = http.crearCategoria(ana, p, "Comida");
        http.asignar(ana, p, "2000-01", comida, 90_000L);

        autoAsignar(ana, "2000-02", "PROMEDIO_ASIGNADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(30_000));
        autoAsignar(ana, "2000-01", "ASIGNADO_MES_PASADO", null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cambios.length()").value(1))
                .andExpect(jsonPath("$.cambios[0].asignadoDespues").value(0));
    }
}
