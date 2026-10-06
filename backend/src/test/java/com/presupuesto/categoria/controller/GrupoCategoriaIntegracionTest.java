package com.presupuesto.categoria.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GrupoCategoriaIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    /** Grupos con los que nace todo presupuesto: los de cada test quedan a continuación. */
    private static final String[] INICIALES = {"Facturas", "Necesidades", "Deseos", "Ahorro"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{\"nombre\": \"Vivienda\", \"posicion\": 0}";

        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/ocultar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/mostrar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/mover")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
    }

    // ---------- crear y validar ----------

    @Test
    void crearDevuelve201ConOrdenAlFinalYVisible() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Vivienda")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nombre").value("Vivienda"))
                .andExpect(jsonPath("$.orden").value(INICIALES.length))
                .andExpect(jsonPath("$.oculto").value(false))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
    }

    @Test
    void elOrdenAlCrearEsConsecutivo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        int n = INICIALES.length;
        crear(ana, ana.presupuestoId, "Uno").andExpect(jsonPath("$.orden").value(n));
        crear(ana, ana.presupuestoId, "Dos").andExpect(jsonPath("$.orden").value(n + 1));
        crear(ana, ana.presupuestoId, "Tres").andExpect(jsonPath("$.orden").value(n + 2));
    }

    @Test
    void elNombreSeGuardaRecortado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "  Comida  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Comida"));
    }

    @Test
    void unNombreVacioEnBlancoOAusenteDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "   "), "nombre");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, ""), "nombre");
        esperarDatosInvalidos(enviar(ana, ruta(ana.presupuestoId), Map.of()), "nombre");
    }

    @Test
    void elLimiteDelNombreEsDeCienCaracteres() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "a".repeat(101)), "nombre");
        crear(ana, ana.presupuestoId, "a".repeat(100)).andExpect(status().isCreated());
    }

    // ---------- duplicados ----------

    @Test
    void unNombreRepetidoSinDistinguirMayusculasDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Vivienda").andExpect(status().isCreated());

        crear(ana, ana.presupuestoId, "vivienda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_CATEGORIA_YA_EXISTE"));
    }

    @Test
    void renombrarAUnNombreExistenteDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Vivienda").andExpect(status().isCreated());
        long comida = idDe(crear(ana, ana.presupuestoId, "Comida"));

        renombrar(ana, ana.presupuestoId, comida, "VIVIENDA")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("GRUPO_CATEGORIA_YA_EXISTE"));
    }

    @Test
    void renombrarCambiandoSoloLaCapitalizacionEsValido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "vivienda"));

        renombrar(ana, ana.presupuestoId, id, "Vivienda")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Vivienda"));
    }

    @Test
    void elMismoNombreEnOtroPresupuestoDelMismoUsuarioSePermite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        crear(ana, ana.presupuestoId, "Vivienda").andExpect(status().isCreated());

        crear(ana, otro, "Vivienda").andExpect(status().isCreated());
    }

    // ---------- renombrar ----------

    @Test
    void renombrarDevuelve200ConElMismoOrden() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Uno");
        long id = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        renombrar(ana, ana.presupuestoId, id, "Hogar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hogar"))
                .andExpect(jsonPath("$.orden").value(INICIALES.length + 1));
    }

    @Test
    void renombrarIgnoraOrdenYOculto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Uno");
        long id = idDe(crear(ana, ana.presupuestoId, "Dos"));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Hogar");
        cuerpo.put("orden", 5);
        cuerpo.put("oculto", true);

        mockMvc.perform(put(ruta(ana.presupuestoId) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orden").value(INICIALES.length + 1))
                .andExpect(jsonPath("$.oculto").value(false));
    }

    @Test
    void renombrarConNombreInvalidoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        esperarDatosInvalidos(renombrar(ana, ana.presupuestoId, id, "  "), "nombre");
    }

    // ---------- ocultar y mostrar ----------

    @Test
    void ocultarYMostrarSonIdempotentes() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        accion(ana, ana.presupuestoId, id, "ocultar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculto").value(true));
        accion(ana, ana.presupuestoId, id, "ocultar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculto").value(true));
        accion(ana, ana.presupuestoId, id, "mostrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculto").value(false));
        accion(ana, ana.presupuestoId, id, "mostrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculto").value(false));
    }

    // ---------- mover ----------

    @Test
    void moverHaciaAdelanteYHaciaAtrasRenumeraSinHuecos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long a = idDe(crear(ana, ana.presupuestoId, "A"));
        long b = idDe(crear(ana, ana.presupuestoId, "B"));
        long c = idDe(crear(ana, ana.presupuestoId, "C"));

        int n = INICIALES.length;
        mover(ana, ana.presupuestoId, a, n + 2)
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(n + 2));
        esperarOrden(ana, ana.presupuestoId, "B", "C", "A");

        mover(ana, ana.presupuestoId, c, n)
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(n));
        esperarOrden(ana, ana.presupuestoId, "C", "B", "A");

        mover(ana, ana.presupuestoId, b, n + 1).andExpect(status().isOk());
        esperarOrden(ana, ana.presupuestoId, "C", "B", "A");
    }

    @Test
    void moverIncluyeLosGruposOcultosEnLaRenumeracion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "A");
        long b = idDe(crear(ana, ana.presupuestoId, "B"));
        long c = idDe(crear(ana, ana.presupuestoId, "C"));
        accion(ana, ana.presupuestoId, b, "ocultar").andExpect(status().isOk());

        mover(ana, ana.presupuestoId, c, INICIALES.length).andExpect(status().isOk());

        esperarOrden(ana, ana.presupuestoId, "C", "A", "B");
    }

    @Test
    void moverFueraDeRangoDevuelve400ConCodigoDatosInvalidosYNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long a = idDe(crear(ana, ana.presupuestoId, "A"));
        crear(ana, ana.presupuestoId, "B");

        mover(ana, ana.presupuestoId, a, INICIALES.length + 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        esperarDatosInvalidos(mover(ana, ana.presupuestoId, a, -1), "posicion");
        esperarOrden(ana, ana.presupuestoId, "A", "B");
    }

    @Test
    void moverSinPosicionDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long a = idDe(crear(ana, ana.presupuestoId, "A"));

        esperarDatosInvalidos(enviar(ana, ruta(ana.presupuestoId) + "/" + a + "/mover", Map.of()),
                "posicion");
    }

    // ---------- aislamiento ----------

    @Test
    void elPresupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long grupo = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        esperarNoEncontrado(crear(beto, ana.presupuestoId, "Hackeo"));
        esperarNoEncontrado(renombrar(beto, ana.presupuestoId, grupo, "Hackeo"));
        esperarNoEncontrado(accion(beto, ana.presupuestoId, grupo, "ocultar"));
        esperarNoEncontrado(accion(beto, ana.presupuestoId, grupo, "mostrar"));
        esperarNoEncontrado(mover(beto, ana.presupuestoId, grupo, 0));
        esperarOrden(ana, ana.presupuestoId, "Vivienda");
    }

    @Test
    void elGrupoDeOtraPersonaPorLaUrlDelPropioPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long grupo = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        esperarNoEncontrado(renombrar(beto, beto.presupuestoId, grupo, "Hackeo"));
        esperarNoEncontrado(accion(beto, beto.presupuestoId, grupo, "ocultar"));
        esperarNoEncontrado(accion(beto, beto.presupuestoId, grupo, "mostrar"));
        esperarNoEncontrado(mover(beto, beto.presupuestoId, grupo, 0));
    }

    @Test
    void elGrupoDeOtroPresupuestoDelMismoUsuarioDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long grupo = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        esperarNoEncontrado(renombrar(ana, otro, grupo, "Hogar"));
        esperarNoEncontrado(accion(ana, otro, grupo, "ocultar"));
        esperarNoEncontrado(accion(ana, otro, grupo, "mostrar"));
        esperarNoEncontrado(mover(ana, otro, grupo, 0));
    }

    @Test
    void unPresupuestoOUnGrupoInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarNoEncontrado(crear(ana, 999_999L, "Vivienda"));
        esperarNoEncontrado(renombrar(ana, ana.presupuestoId, 999_999L, "Hogar"));
        esperarNoEncontrado(accion(ana, ana.presupuestoId, 999_999L, "ocultar"));
        esperarNoEncontrado(mover(ana, ana.presupuestoId, 999_999L, 0));
    }

    // ---------- sin borrado ----------

    @Test
    void deleteDevuelve405() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Vivienda"));

        mockMvc.perform(delete(ruta(ana.presupuestoId) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- utilidades ----------

    private record Sesion(String token, long presupuestoId) {
    }

    private Sesion registrar(String email) throws Exception {
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
        String token = objectMapper.readTree(cuerpo).get("token").asString();
        String presupuestos = mockMvc.perform(get(RUTA_PRESUPUESTOS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Toda persona recibe "Mi presupuesto" al registrarse: es el único de la lista.
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    private ResultActions crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return enviar(sesion, RUTA_PRESUPUESTOS, Map.of("nombre", nombre));
    }

    private ResultActions crear(Sesion sesion, long presupuestoId, String nombre)
            throws Exception {
        return enviar(sesion, ruta(presupuestoId), Map.of("nombre", nombre));
    }

    private ResultActions renombrar(Sesion sesion, long presupuestoId, long id, String nombre)
            throws Exception {
        return mockMvc.perform(put(ruta(presupuestoId) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nombre", nombre))));
    }

    private ResultActions accion(Sesion sesion, long presupuestoId, long id, String accion)
            throws Exception {
        return mockMvc.perform(post(ruta(presupuestoId) + "/" + id + "/" + accion)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions mover(Sesion sesion, long presupuestoId, long id, int posicion)
            throws Exception {
        return enviar(sesion, ruta(presupuestoId) + "/" + id + "/mover",
                Map.of("posicion", posicion));
    }

    private ResultActions enviar(Sesion sesion, String ruta, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(post(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    /**
     * Comprueba el orden de todos los grupos (ocultos incluidos) con el árbol de categorías:
     * primero los iniciales y después los {@code nombres} creados por el test.
     */
    private void esperarOrden(Sesion sesion, long presupuestoId, String... creados)
            throws Exception {
        String[] nombres = new String[INICIALES.length + creados.length];
        System.arraycopy(INICIALES, 0, nombres, 0, INICIALES.length);
        System.arraycopy(creados, 0, nombres, INICIALES.length, creados.length);
        String cuerpo = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId
                                + "/categorias")
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arbol = objectMapper.readTree(cuerpo);
        org.assertj.core.api.Assertions.assertThat(arbol.size()).isEqualTo(nombres.length);
        for (int i = 0; i < nombres.length; i++) {
            org.assertj.core.api.Assertions.assertThat(arbol.get(i).get("nombre").asString())
                    .isEqualTo(nombres[i]);
            org.assertj.core.api.Assertions.assertThat(arbol.get(i).get("orden").asInt())
                    .isEqualTo(i);
        }
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/grupos-categorias";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    private static void esperarNoEncontrado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    private static void esperarDatosInvalidos(ResultActions resultado, String campo)
            throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }
}
