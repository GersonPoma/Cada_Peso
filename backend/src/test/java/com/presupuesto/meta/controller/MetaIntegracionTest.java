package com.presupuesto.meta.controller;

import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarDatosInvalidos;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarDatosInvalidosSinCampos;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarNoAutenticado;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.esperarNoEncontrado;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.metaMensual;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.rutaMes;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.rutaMeta;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.rutaMetas;
import static com.presupuesto.meta.controller.ApoyoHttpMeta.saldoObjetivo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.meta.controller.ApoyoHttpMeta.Sesion;
import com.presupuesto.meta.entity.Meta;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetaIntegracionTest {

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

    private ResultActions guardar(
            Sesion sesion, long presupuestoId, long categoriaId, Map<String, Object> cuerpo)
            throws Exception {
        return http.enviar(put(rutaMeta(presupuestoId, categoriaId)), sesion, cuerpo);
    }

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDeMetasDevuelven401() throws Exception {
        esperarNoAutenticado(mockMvc.perform(get(rutaMeta(1, 1))));
        esperarNoAutenticado(mockMvc.perform(put(rutaMeta(1, 1))
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
        esperarNoAutenticado(mockMvc.perform(delete(rutaMeta(1, 1))));
        esperarNoAutenticado(mockMvc.perform(get(rutaMetas(1))));
    }

    // ---------- aislamiento ----------

    @Test
    void unPresupuestoAjenoResponde404EnCadaOperacionYNoCambiaNada() throws Exception {
        Sesion ana = http.registrar("ana-meta@ejemplo.com");
        Sesion beto = http.registrar("beto-meta@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        guardar(ana, ana.presupuestoId(), comida, metaMensual(100_000L))
                .andExpect(status().isOk());

        esperarNoEncontrado(http.enviar(put(rutaMeta(ana.presupuestoId(), comida)), beto,
                saldoObjetivo(1L)));
        esperarNoEncontrado(http.consultar(beto, rutaMeta(ana.presupuestoId(), comida)));
        esperarNoEncontrado(http.borrar(beto, rutaMeta(ana.presupuestoId(), comida)));
        esperarNoEncontrado(http.consultar(beto, rutaMetas(ana.presupuestoId())));

        http.consultar(ana, rutaMeta(ana.presupuestoId(), comida))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("MONTO_MENSUAL"))
                .andExpect(jsonPath("$.monto").value(100_000));
    }

    @Test
    void unaCategoriaDeOtraPersonaODeOtroPresupuestoResponde404() throws Exception {
        Sesion ana = http.registrar("ana-meta2@ejemplo.com");
        Sesion beto = http.registrar("beto-meta2@ejemplo.com");
        long deAna = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        long segundo = http.crearPresupuesto(ana, "Segundo");
        guardar(ana, ana.presupuestoId(), deAna, metaMensual(100_000L))
                .andExpect(status().isOk());

        // Otra persona, por la URL de su propio presupuesto.
        esperarNoEncontrado(http.enviar(put(rutaMeta(beto.presupuestoId(), deAna)), beto,
                saldoObjetivo(1L)));
        esperarNoEncontrado(http.consultar(beto, rutaMeta(beto.presupuestoId(), deAna)));
        esperarNoEncontrado(http.borrar(beto, rutaMeta(beto.presupuestoId(), deAna)));
        // La misma persona, por la URL de su otro presupuesto.
        esperarNoEncontrado(http.enviar(put(rutaMeta(segundo, deAna)), ana, saldoObjetivo(1L)));
        esperarNoEncontrado(http.consultar(ana, rutaMeta(segundo, deAna)));
        esperarNoEncontrado(http.borrar(ana, rutaMeta(segundo, deAna)));

        assertThat(metaRepository.findByCategoriaId(deAna)).isPresent();
    }

    @Test
    void unaCategoriaOUnPresupuestoInexistenteResponde404() throws Exception {
        Sesion ana = http.registrar("ana-meta3@ejemplo.com");

        esperarNoEncontrado(http.enviar(
                put(rutaMeta(ana.presupuestoId(), Long.MAX_VALUE)), ana, saldoObjetivo(1L)));
        esperarNoEncontrado(http.consultar(ana, rutaMeta(ana.presupuestoId(), Long.MAX_VALUE)));
        esperarNoEncontrado(http.consultar(ana, rutaMetas(Long.MAX_VALUE)));
    }

    // ---------- guardar ----------

    @Test
    void guardarCreaLaMetaYElGetDevuelveLoMismo() throws Exception {
        Sesion ana = http.registrar("ana-meta4@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");

        guardar(ana, ana.presupuestoId(), comida, metaMensual(100_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(comida))
                .andExpect(jsonPath("$.tipo").value("MONTO_MENSUAL"))
                .andExpect(jsonPath("$.monto").value(100_000))
                .andExpect(jsonPath("$.frecuencia").value("MENSUAL"))
                .andExpect(jsonPath("$.diaSemana").doesNotExist())
                .andExpect(jsonPath("$.intervaloDias").doesNotExist())
                .andExpect(jsonPath("$.fechaInicio").doesNotExist())
                .andExpect(jsonPath("$.fechaObjetivo").doesNotExist());
        http.consultar(ana, rutaMeta(ana.presupuestoId(), comida))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(comida))
                .andExpect(jsonPath("$.monto").value(100_000));
    }

    @Test
    void guardarLasVariantesDeCadaTipoDevuelveSusCampos() throws Exception {
        Sesion ana = http.registrar("ana-meta5@ejemplo.com");
        long a = http.crearCategoria(ana, ana.presupuestoId(), "A");
        long b = http.crearCategoria(ana, ana.presupuestoId(), "B");
        long c = http.crearCategoria(ana, ana.presupuestoId(), "C");
        Map<String, Object> semanal = metaMensual(20_000L);
        semanal.put("frecuencia", "SEMANAL");
        semanal.put("diaSemana", 1);
        Map<String, Object> personalizada = metaMensual(10_000L);
        personalizada.put("frecuencia", "PERSONALIZADA");
        personalizada.put("intervaloDias", 14);
        personalizada.put("fechaInicio", "2026-10-02");
        Map<String, Object> paraFecha = new LinkedHashMap<>();
        paraFecha.put("tipo", "MONTO_PARA_FECHA");
        paraFecha.put("monto", 600_000L);
        paraFecha.put("fechaObjetivo", "2026-12-15");

        guardar(ana, ana.presupuestoId(), a, semanal)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").value("SEMANAL"))
                .andExpect(jsonPath("$.diaSemana").value(1));
        guardar(ana, ana.presupuestoId(), b, personalizada)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervaloDias").value(14))
                .andExpect(jsonPath("$.fechaInicio").value("2026-10-02"));
        guardar(ana, ana.presupuestoId(), c, paraFecha)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("MONTO_PARA_FECHA"))
                .andExpect(jsonPath("$.fechaObjetivo").value("2026-12-15"));
    }

    @Test
    void reemplazarNoDuplicaYDejaLaMetaNuevaSinLosCamposDelTipoAnterior() throws Exception {
        Sesion ana = http.registrar("ana-meta6@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        Map<String, Object> semanal = metaMensual(20_000L);
        semanal.put("frecuencia", "SEMANAL");
        semanal.put("diaSemana", 3);
        guardar(ana, ana.presupuestoId(), comida, semanal).andExpect(status().isOk());
        long idAntes = metaRepository.findByCategoriaId(comida).orElseThrow().getId();

        guardar(ana, ana.presupuestoId(), comida, saldoObjetivo(300_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("SALDO_OBJETIVO"))
                .andExpect(jsonPath("$.monto").value(300_000))
                .andExpect(jsonPath("$.frecuencia").doesNotExist())
                .andExpect(jsonPath("$.diaSemana").doesNotExist());

        List<Meta> metas = metaRepository.findDelPresupuestoEnOrdenDelArbol(ana.presupuestoId());
        assertThat(metas).hasSize(1);
        assertThat(metas.get(0).getId()).isEqualTo(idAntes);
        http.consultar(ana, rutaMetas(ana.presupuestoId()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reemplazarConservaLasPospuestas() throws Exception {
        Sesion ana = http.registrar("ana-meta7@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        guardar(ana, ana.presupuestoId(), comida, metaMensual(100_000L))
                .andExpect(status().isOk());
        http.accion(ana, rutaMes(ana.presupuestoId(), "2026-10") + "/metas/" + comida
                + "/posponer").andExpect(status().isOk());

        guardar(ana, ana.presupuestoId(), comida, saldoObjetivo(300_000L))
                .andExpect(status().isOk());

        http.consultar(ana, rutaMes(ana.presupuestoId(), "2026-10") + "/metas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metas[0].tipo").value("SALDO_OBJETIVO"))
                .andExpect(jsonPath("$.metas[0].estado").value("POSPUESTA"))
                .andExpect(jsonPath("$.metas[0].necesidad").value(0));
        http.consultar(ana, rutaMes(ana.presupuestoId(), "2026-11") + "/metas")
                .andExpect(jsonPath("$.metas[0].estado").value("FALTA"));
        Long metaId = metaRepository.findByCategoriaId(comida).orElseThrow().getId();
        assertThat(pospuestaRepository.countByMetaId(metaId)).isEqualTo(1);
    }

    @Test
    void unaCategoriaOcultaPuedeTenerMeta() throws Exception {
        Sesion ana = http.registrar("ana-meta8@ejemplo.com");
        long vieja = http.crearCategoria(ana, ana.presupuestoId(), "Vieja");
        http.ocultarCategoria(ana, ana.presupuestoId(), vieja);

        guardar(ana, ana.presupuestoId(), vieja, metaMensual(5_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(vieja));
        http.consultar(ana, rutaMeta(ana.presupuestoId(), vieja)).andExpect(status().isOk());
    }

    // ---------- validación ----------

    @Test
    void faltantesPorTipoDevuelven400ConElCampoEnErrores() throws Exception {
        Sesion ana = http.registrar("ana-meta9@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        long p = ana.presupuestoId();

        Map<String, Object> sinFrecuencia = metaMensual(1L);
        sinFrecuencia.remove("frecuencia");
        esperarDatosInvalidos(guardar(ana, p, comida, sinFrecuencia), "frecuencia");

        Map<String, Object> semanalSinDia = metaMensual(1L);
        semanalSinDia.put("frecuencia", "SEMANAL");
        esperarDatosInvalidos(guardar(ana, p, comida, semanalSinDia), "diaSemana");

        Map<String, Object> personalizadaVacia = metaMensual(1L);
        personalizadaVacia.put("frecuencia", "PERSONALIZADA");
        esperarDatosInvalidos(guardar(ana, p, comida, personalizadaVacia), "intervaloDias");
        esperarDatosInvalidos(guardar(ana, p, comida, personalizadaVacia), "fechaInicio");

        Map<String, Object> sinFecha = new LinkedHashMap<>();
        sinFecha.put("tipo", "MONTO_PARA_FECHA");
        sinFecha.put("monto", 1L);
        esperarDatosInvalidos(guardar(ana, p, comida, sinFecha), "fechaObjetivo");

        esperarDatosInvalidos(guardar(ana, p, comida, Map.of("monto", 1L)), "tipo");
        esperarDatosInvalidos(guardar(ana, p, comida, Map.of("tipo", "SALDO_OBJETIVO")),
                "monto");
        assertThat(metaRepository.findByCategoriaId(comida)).isEmpty();
    }

    @Test
    void valoresFueraDeRangoDevuelven400YNoCambianLaMetaExistente() throws Exception {
        Sesion ana = http.registrar("ana-meta10@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        long p = ana.presupuestoId();
        guardar(ana, p, comida, metaMensual(100_000L)).andExpect(status().isOk());

        esperarDatosInvalidos(guardar(ana, p, comida, saldoObjetivo(0L)), "monto");
        esperarDatosInvalidos(guardar(ana, p, comida, saldoObjetivo(-5L)), "monto");
        for (int dia : new int[] {0, 8}) {
            Map<String, Object> cuerpo = metaMensual(1L);
            cuerpo.put("frecuencia", "SEMANAL");
            cuerpo.put("diaSemana", dia);
            esperarDatosInvalidos(guardar(ana, p, comida, cuerpo), "diaSemana");
        }
        for (int dias : new int[] {1, 366}) {
            Map<String, Object> cuerpo = metaMensual(1L);
            cuerpo.put("frecuencia", "PERSONALIZADA");
            cuerpo.put("intervaloDias", dias);
            cuerpo.put("fechaInicio", "2026-10-02");
            esperarDatosInvalidos(guardar(ana, p, comida, cuerpo), "intervaloDias");
        }
        Map<String, Object> tipoRaro = saldoObjetivo(1L);
        tipoRaro.put("tipo", "RARO");
        esperarDatosInvalidosSinCampos(guardar(ana, p, comida, tipoRaro));
        Map<String, Object> frecuenciaRara = metaMensual(1L);
        frecuenciaRara.put("frecuencia", "DIARIA");
        esperarDatosInvalidosSinCampos(guardar(ana, p, comida, frecuenciaRara));

        http.consultar(ana, rutaMeta(p, comida))
                .andExpect(jsonPath("$.tipo").value("MONTO_MENSUAL"))
                .andExpect(jsonPath("$.monto").value(100_000));
    }

    @Test
    void losCamposQueNoAplicanAlTipoSeDescartan() throws Exception {
        Sesion ana = http.registrar("ana-meta11@ejemplo.com");
        long a = http.crearCategoria(ana, ana.presupuestoId(), "A");
        long b = http.crearCategoria(ana, ana.presupuestoId(), "B");
        Map<String, Object> saldo = saldoObjetivo(300_000L);
        saldo.put("frecuencia", "SEMANAL");
        saldo.put("diaSemana", 9);
        saldo.put("intervaloDias", 1);
        saldo.put("fechaInicio", "2026-10-02");
        saldo.put("fechaObjetivo", "2026-12-15");
        Map<String, Object> mensual = metaMensual(100_000L);
        mensual.put("diaSemana", 3);
        mensual.put("fechaObjetivo", "2026-12-15");

        guardar(ana, ana.presupuestoId(), a, saldo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").doesNotExist())
                .andExpect(jsonPath("$.diaSemana").doesNotExist())
                .andExpect(jsonPath("$.intervaloDias").doesNotExist())
                .andExpect(jsonPath("$.fechaInicio").doesNotExist())
                .andExpect(jsonPath("$.fechaObjetivo").doesNotExist());
        guardar(ana, ana.presupuestoId(), b, mensual)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").doesNotExist())
                .andExpect(jsonPath("$.fechaObjetivo").doesNotExist());
        Meta guardada = metaRepository.findByCategoriaId(a).orElseThrow();
        assertThat(guardada.getFrecuencia()).isNull();
        assertThat(guardada.getDiaSemana()).isNull();
        assertThat(guardada.getFechaObjetivo()).isNull();
    }

    @Test
    void unaFechaObjetivoPasadaSePermite() throws Exception {
        Sesion ana = http.registrar("ana-meta12@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "MONTO_PARA_FECHA");
        cuerpo.put("monto", 1L);
        cuerpo.put("fechaObjetivo", "2001-01-31");

        guardar(ana, ana.presupuestoId(), comida, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaObjetivo").value("2001-01-31"));
    }

    // ---------- consultar y borrar ----------

    @Test
    void unaCategoriaSinMetaResponde404AlConsultarYAlBorrar() throws Exception {
        Sesion ana = http.registrar("ana-meta13@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");

        esperarNoEncontrado(http.consultar(ana, rutaMeta(ana.presupuestoId(), comida)));
        esperarNoEncontrado(http.borrar(ana, rutaMeta(ana.presupuestoId(), comida)));
    }

    @Test
    void borrarDevuelve204YLaMetaDejaDeExistir() throws Exception {
        Sesion ana = http.registrar("ana-meta14@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        guardar(ana, ana.presupuestoId(), comida, metaMensual(1L))
                .andExpect(status().isOk());

        http.borrar(ana, rutaMeta(ana.presupuestoId(), comida))
                .andExpect(status().isNoContent());

        esperarNoEncontrado(http.consultar(ana, rutaMeta(ana.presupuestoId(), comida)));
        esperarNoEncontrado(http.borrar(ana, rutaMeta(ana.presupuestoId(), comida)));
    }

    @Test
    void borrarUnaMetaConPospuestasBorraTambienLasPospuestas() throws Exception {
        Sesion ana = http.registrar("ana-meta15@ejemplo.com");
        long comida = http.crearCategoria(ana, ana.presupuestoId(), "Comida");
        long otra = http.crearCategoria(ana, ana.presupuestoId(), "Otra");
        long p = ana.presupuestoId();
        guardar(ana, p, comida, metaMensual(1L)).andExpect(status().isOk());
        guardar(ana, p, otra, metaMensual(1L)).andExpect(status().isOk());
        for (String mes : List.of("2026-10", "2026-11")) {
            http.accion(ana, rutaMes(p, mes) + "/metas/" + comida + "/posponer")
                    .andExpect(status().isOk());
        }
        http.accion(ana, rutaMes(p, "2026-10") + "/metas/" + otra + "/posponer")
                .andExpect(status().isOk());
        Long metaId = metaRepository.findByCategoriaId(comida).orElseThrow().getId();
        Long otraId = metaRepository.findByCategoriaId(otra).orElseThrow().getId();
        assertThat(pospuestaRepository.countByMetaId(metaId)).isEqualTo(2);

        http.borrar(ana, rutaMeta(p, comida)).andExpect(status().isNoContent());

        assertThat(metaRepository.findByCategoriaId(comida)).isEmpty();
        assertThat(pospuestaRepository.countByMetaId(metaId)).isZero();
        assertThat(pospuestaRepository.countByMetaId(otraId)).isEqualTo(1);
        assertThat(metaRepository.findByCategoriaId(otra)).isPresent();
    }

    // ---------- listar ----------

    @Test
    void listarDevuelveSoloLasMetasDelPresupuestoEnElOrdenDelArbolConLasOcultas()
            throws Exception {
        Sesion ana = http.registrar("ana-meta16@ejemplo.com");
        long segundoPresupuesto = http.crearPresupuesto(ana, "Segundo");
        long primera = http.crearCategoria(ana, ana.presupuestoId(), "Primera");
        long segunda = http.crearCategoria(ana, ana.presupuestoId(), "Segunda");
        long oculta = http.crearCategoria(ana, ana.presupuestoId(), "Tercera");
        long ajena = http.crearCategoria(ana, segundoPresupuesto, "Ajena");
        http.ocultarCategoria(ana, ana.presupuestoId(), oculta);
        guardar(ana, ana.presupuestoId(), oculta, saldoObjetivo(3L))
                .andExpect(status().isOk());
        guardar(ana, ana.presupuestoId(), primera, saldoObjetivo(1L))
                .andExpect(status().isOk());
        guardar(ana, segundoPresupuesto, ajena, saldoObjetivo(9L))
                .andExpect(status().isOk());
        guardar(ana, ana.presupuestoId(), segunda, saldoObjetivo(2L))
                .andExpect(status().isOk());

        JsonNode lista = http.cuerpo(http.consultar(ana, rutaMetas(ana.presupuestoId()))
                .andExpect(status().isOk()));

        assertThat(lista.size()).isEqualTo(3);
        assertThat(lista.get(0).get("categoriaId").asLong()).isEqualTo(primera);
        assertThat(lista.get(1).get("categoriaId").asLong()).isEqualTo(segunda);
        assertThat(lista.get(2).get("categoriaId").asLong()).isEqualTo(oculta);
    }

    @Test
    void sinMetasLaListaEstaVacia() throws Exception {
        Sesion ana = http.registrar("ana-meta17@ejemplo.com");

        http.consultar(ana, rutaMetas(ana.presupuestoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
