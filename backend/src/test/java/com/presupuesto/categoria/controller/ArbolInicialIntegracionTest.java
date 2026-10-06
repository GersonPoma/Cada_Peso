package com.presupuesto.categoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** El árbol con el que nace todo presupuesto, visto desde la API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArbolInicialIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    private static final Map<String, List<String>> ESPERADO = Map.of(
            "Facturas", List.of("Alquiler", "Luz", "Agua", "Internet", "Teléfono"),
            "Necesidades", List.of("Comida", "Transporte", "Salud"),
            "Deseos", List.of("Restaurantes", "Ocio", "Ropa"),
            "Ahorro", List.of("Fondo de emergencia", "Vacaciones"));

    private static final List<String> GRUPOS_EN_ORDEN =
            List.of("Facturas", "Necesidades", "Deseos", "Ahorro");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrarseDejaMiPresupuestoConElArbolInicial() throws Exception {
        String token = registrar("ana@ejemplo.com");

        JsonNode presupuesto = objectMapper.readTree(listarPresupuestos(token)).get(0);
        assertThat(presupuesto.get("nombre").asString()).isEqualTo("Mi presupuesto");

        esperarArbolInicial(token, presupuesto.get("id").asLong());
    }

    @Test
    void crearUnPresupuestoConPostTambienLoDejaConElArbolInicial() throws Exception {
        String token = registrar("ana@ejemplo.com");

        long id = crearPresupuesto(token, "Viajes");

        esperarArbolInicial(token, id);
    }

    @Test
    void cadaPresupuestoTieneSuPropioArbol() throws Exception {
        String token = registrar("ana@ejemplo.com");
        long primero = idDeMiPresupuesto(token);
        long segundo = crearPresupuesto(token, "Viajes");
        JsonNode arbolPrimero = arbol(token, primero);
        JsonNode arbolSegundo = arbol(token, segundo);

        List<Long> idsPrimero = ids(arbolPrimero);
        List<Long> idsSegundo = ids(arbolSegundo);
        assertThat(idsPrimero).doesNotContainAnyElementsOf(idsSegundo);

        // Tocar el árbol del primero no afecta al del segundo.
        long facturas = arbolPrimero.get(0).get("id").asLong();
        long luz = arbolPrimero.get(0).get("categorias").get(1).get("id").asLong();
        mockMvc.perform(put(RUTA_PRESUPUESTOS + "/" + primero + "/grupos-categorias/" + facturas)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Cuentas\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + primero + "/categorias/" + luz + "/ocultar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        assertThat(arbol(token, segundo)).isEqualTo(arbolSegundo);
        esperarArbolInicial(token, segundo);
    }

    @Test
    void losNombresInicialesOcupanSuNombre() throws Exception {
        String token = registrar("ana@ejemplo.com");
        long presupuesto = idDeMiPresupuesto(token);
        long facturas = arbol(token, presupuesto).get(0).get("id").asLong();

        enviar(token, RUTA_PRESUPUESTOS + "/" + presupuesto + "/grupos-categorias",
                Map.of("nombre", "Facturas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_CATEGORIA_YA_EXISTE"));
        enviar(token, RUTA_PRESUPUESTOS + "/" + presupuesto + "/categorias",
                Map.of("grupoId", facturas, "nombre", "Luz"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_YA_EXISTE"));
    }

    @Test
    void unPresupuestoRechazadoPorNombreRepetidoNoCreaArbolNuevo() throws Exception {
        String token = registrar("ana@ejemplo.com");
        crearPresupuesto(token, "Viajes");
        int antes = contarGrupos(token);

        enviar(token, RUTA_PRESUPUESTOS, Map.of("nombre", "Viajes"))
                .andExpect(status().isConflict());

        assertThat(contarGrupos(token)).isEqualTo(antes);
    }

    // ---------- utilidades ----------

    private void esperarArbolInicial(String token, long presupuestoId) throws Exception {
        JsonNode arbol = arbol(token, presupuestoId);
        assertThat(arbol.size()).isEqualTo(GRUPOS_EN_ORDEN.size());
        for (int i = 0; i < GRUPOS_EN_ORDEN.size(); i++) {
            JsonNode grupo = arbol.get(i);
            String nombre = GRUPOS_EN_ORDEN.get(i);
            assertThat(grupo.get("nombre").asString()).isEqualTo(nombre);
            assertThat(grupo.get("orden").asInt()).isEqualTo(i);
            assertThat(grupo.get("oculto").asBoolean()).isFalse();
            List<String> categorias = ESPERADO.get(nombre);
            assertThat(grupo.get("categorias").size()).isEqualTo(categorias.size());
            for (int j = 0; j < categorias.size(); j++) {
                JsonNode categoria = grupo.get("categorias").get(j);
                assertThat(categoria.get("nombre").asString()).isEqualTo(categorias.get(j));
                assertThat(categoria.get("orden").asInt()).isEqualTo(j);
                assertThat(categoria.get("oculta").asBoolean()).isFalse();
            }
        }
    }

    private int contarGrupos(String token) throws Exception {
        int total = 0;
        for (JsonNode presupuesto : objectMapper.readTree(listarPresupuestos(token))) {
            total += arbol(token, presupuesto.get("id").asLong()).size();
        }
        return total;
    }

    private List<Long> ids(JsonNode arbol) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode grupo : arbol) {
            ids.add(grupo.get("id").asLong());
            for (JsonNode categoria : grupo.get("categorias")) {
                ids.add(categoria.get("id").asLong());
            }
        }
        return ids;
    }

    private JsonNode arbol(String token, long presupuestoId) throws Exception {
        String cuerpo = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias")
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private String registrar(String email) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", email);
        datos.put("contrasena", "secreta123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private String listarPresupuestos(String token) throws Exception {
        return mockMvc.perform(get(RUTA_PRESUPUESTOS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long idDeMiPresupuesto(String token) throws Exception {
        return objectMapper.readTree(listarPresupuestos(token)).get(0).get("id").asLong();
    }

    private long crearPresupuesto(String token, String nombre) throws Exception {
        String cuerpo = enviar(token, RUTA_PRESUPUESTOS, Map.of("nombre", nombre))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private ResultActions enviar(String token, String ruta, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(post(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
