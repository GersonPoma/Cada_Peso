package com.presupuesto.beneficiario.controller;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BeneficiarioIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- autenticación y rutas ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{\"nombre\": \"Netflix\"}";

        esperarNoAutenticado(mockMvc.perform(get(ruta)));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
    }

    @Test
    void borrarNoEstaSoportadoYElBeneficiarioSigueExistiendo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", null));

        mockMvc.perform(delete(ruta(ana.presupuestoId) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());

        obtener(ana, ana.presupuestoId, id).andExpect(status().isOk());
    }

    // ---------- crear ----------

    @Test
    void crearMinimoDevuelve201ConElNombreRecortado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "  Tienda Don Pepe  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nombre").value("Tienda Don Pepe"))
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value((Object) null));
    }

    @Test
    void crearConCategoriaPredeterminadaDevuelve201() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");

        crear(ana, ana.presupuestoId, "Netflix", ocio)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value(ocio));
    }

    @Test
    void unaCategoriaOcultaSirveComoCategoriaPredeterminada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long vieja = crearCategoria(ana, ana.presupuestoId, "Vieja");
        accion(ana, ana.presupuestoId, "categorias", vieja, "ocultar").andExpect(status().isOk());

        crear(ana, ana.presupuestoId, "Netflix", vieja)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value(vieja));
    }

    @Test
    void unaCategoriaAjenaOInexistenteDevuelve404YNoCreaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearPresupuesto(ana, "Viajes").andExpect(status().isCreated());
        long viajes = ultimoPresupuesto(ana);
        long ajena = crearCategoria(ana, viajes, "Hotel");
        Sesion beto = registrar("beto@ejemplo.com");
        long deBeto = crearCategoria(beto, beto.presupuestoId, "Comida");

        esperarNoEncontrado(crear(ana, ana.presupuestoId, "Netflix", ajena));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, "Netflix", deBeto));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, "Netflix", 999_999_999L));

        listar(ana, ana.presupuestoId, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unNombreVacioSoloEspaciosAusenteODemasiadoLargoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "", null), "nombre");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "   ", null), "nombre");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "a".repeat(101), null), "nombre");
        esperarDatosInvalidos(enviar(post(ruta(ana.presupuestoId)), ana, Map.of()), "nombre");
        crear(ana, ana.presupuestoId, "a".repeat(100), null).andExpect(status().isCreated());
    }

    @Test
    void unCuerpoVacioOMalFormadoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        mockMvc.perform(post(ruta(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mockMvc.perform(post(ruta(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    // ---------- unicidad ----------

    @Test
    void unNombreRepetidoSinDistinguirMayusculasDevuelve409ConSuCodigo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Netflix", null).andExpect(status().isCreated());

        crear(ana, ana.presupuestoId, "  NETFLIX ", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BENEFICIARIO_YA_EXISTE"));
    }

    @Test
    void elMismoNombreEsValidoEnOtroPresupuestoDeLaMismaPersonaYDeOtra() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearPresupuesto(ana, "Viajes").andExpect(status().isCreated());
        long viajes = ultimoPresupuesto(ana);
        Sesion beto = registrar("beto@ejemplo.com");
        crear(ana, ana.presupuestoId, "Netflix", null).andExpect(status().isCreated());

        crear(ana, viajes, "Netflix", null).andExpect(status().isCreated());
        crear(beto, beto.presupuestoId, "Netflix", null).andExpect(status().isCreated());
    }

    // ---------- listar y buscar ----------

    @Test
    void laListaEstaOrdenadaSinDistinguirMayusculasYEsDelPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearPresupuesto(ana, "Viajes").andExpect(status().isCreated());
        long viajes = ultimoPresupuesto(ana);
        crear(ana, ana.presupuestoId, "zapateria", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "Banco", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "alquiler", null).andExpect(status().isCreated());
        crear(ana, viajes, "Hotel", null).andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nombre").value("alquiler"))
                .andExpect(jsonPath("$[1].nombre").value("Banco"))
                .andExpect(jsonPath("$[2].nombre").value("zapateria"))
                .andExpect(jsonPath("$[0].id", notNullValue()));
    }

    @Test
    void unPresupuestoSinBeneficiariosDevuelveUnaListaVacia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        listar(ana, ana.presupuestoId, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void qBuscaPorPrefijoSinDistinguirMayusculasYOrdenado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Netflix", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "Nestle", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "Banco Union", null).andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, "NE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Nestle"))
                .andExpect(jsonPath("$[1].nombre").value("Netflix"));
        listar(ana, ana.presupuestoId, "union", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void losComodinesEnQSonTextoLiteral() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "100% Natural", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "100 Natural", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "a_b", null).andExpect(status().isCreated());
        crear(ana, ana.presupuestoId, "axb", null).andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, "100%", null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("100% Natural"));
        listar(ana, ana.presupuestoId, "a_", null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("a_b"));
        listar(ana, ana.presupuestoId, "%", null)
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elLimiteEsDiezPorDefectoConQYSeAcotaConLimite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        for (int i = 10; i < 25; i++) {
            crear(ana, ana.presupuestoId, "a" + i, null).andExpect(status().isCreated());
        }

        listar(ana, ana.presupuestoId, "a", null)
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].nombre").value("a10"));
        listar(ana, ana.presupuestoId, "a", 3)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].nombre").value("a12"));
    }

    @Test
    void sinQNoSeAplicaElLimite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        for (int i = 10; i < 25; i++) {
            crear(ana, ana.presupuestoId, "a" + i, null).andExpect(status().isCreated());
        }

        listar(ana, ana.presupuestoId, null, null).andExpect(jsonPath("$", hasSize(15)));
        listar(ana, ana.presupuestoId, null, 3).andExpect(jsonPath("$", hasSize(15)));
        listar(ana, ana.presupuestoId, "   ", 3).andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    void unLimiteFueraDeRangoDevuelve400ConSuCodigo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        for (int limite : new int[] {0, -1, 51}) {
            listar(ana, ana.presupuestoId, "a", limite)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
        listar(ana, ana.presupuestoId, "a", 1).andExpect(status().isOk());
        listar(ana, ana.presupuestoId, "a", 50).andExpect(status().isOk());
    }

    @Test
    void unLimiteQueNoEsNumeroDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        mockMvc.perform(get(ruta(ana.presupuestoId))
                        .param("q", "a")
                        .param("limite", "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    // ---------- detalle ----------

    @Test
    void elDetalleDevuelveElBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", ocio));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Netflix"))
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value(ocio));
    }

    // ---------- editar ----------

    @Test
    void editarRenombraYCambiaLaCategoria() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", null));

        editar(ana, ana.presupuestoId, id, cuerpo("  Netflix Bolivia ", ocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Netflix Bolivia"))
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value(ocio));
    }

    @Test
    void editarSinCategoriaIdLaQuita() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", ocio));

        editar(ana, ana.presupuestoId, id, cuerpo("Netflix", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value((Object) null));
        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.categoriaPredeterminadaId").value((Object) null));
    }

    @Test
    void cambiarSoloLaCapitalizacionDelPropioNombreEsValido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "netflix", null));

        editar(ana, ana.presupuestoId, id, cuerpo("Netflix", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Netflix"));
    }

    @Test
    void editarAlNombreDeOtroBeneficiarioDevuelve409YNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Netflix", null).andExpect(status().isCreated());
        long spotify = idDe(crear(ana, ana.presupuestoId, "Spotify", null));

        editar(ana, ana.presupuestoId, spotify, cuerpo("netflix", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BENEFICIARIO_YA_EXISTE"));
        obtener(ana, ana.presupuestoId, spotify)
                .andExpect(jsonPath("$.nombre").value("Spotify"));
    }

    @Test
    void editarConCategoriaAjenaDevuelve404YNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long deBeto = crearCategoria(beto, beto.presupuestoId, "Comida");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", null));

        esperarNoEncontrado(editar(ana, ana.presupuestoId, id, cuerpo("Otro", deBeto)));

        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.nombre").value("Netflix"));
    }

    @Test
    void editarConDatosInvalidosDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Netflix", null));

        esperarDatosInvalidos(editar(ana, ana.presupuestoId, id, cuerpo("", null)), "nombre");
        esperarDatosInvalidos(
                editar(ana, ana.presupuestoId, id, cuerpo("a".repeat(101), null)), "nombre");
    }

    // ---------- aislamiento ----------

    @Test
    void elPresupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long deBeto = idDe(crear(beto, beto.presupuestoId, "Netflix", null));

        esperarNoEncontrado(listar(ana, beto.presupuestoId, null, null));
        esperarNoEncontrado(listar(ana, beto.presupuestoId, "n", 5));
        esperarNoEncontrado(crear(ana, beto.presupuestoId, "Spotify", null));
        esperarNoEncontrado(obtener(ana, beto.presupuestoId, deBeto));
        esperarNoEncontrado(editar(ana, beto.presupuestoId, deBeto, cuerpo("X", null)));
    }

    @Test
    void unBeneficiarioDeOtroPresupuestoDeLaMismaPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearPresupuesto(ana, "Viajes").andExpect(status().isCreated());
        long viajes = ultimoPresupuesto(ana);
        long deViajes = idDe(crear(ana, viajes, "Hotel", null));

        esperarNoEncontrado(obtener(ana, ana.presupuestoId, deViajes));
        esperarNoEncontrado(editar(ana, ana.presupuestoId, deViajes, cuerpo("X", null)));
        obtener(ana, viajes, deViajes).andExpect(status().isOk());
    }

    @Test
    void unPresupuestoOUnBeneficiarioInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarNoEncontrado(listar(ana, 999_999_999L, null, null));
        esperarNoEncontrado(crear(ana, 999_999_999L, "Netflix", null));
        esperarNoEncontrado(obtener(ana, ana.presupuestoId, 999_999_999L));
        esperarNoEncontrado(
                editar(ana, ana.presupuestoId, 999_999_999L, cuerpo("Netflix", null)));
    }

    // ---------- ayudas ----------

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
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(cuerpo).get("token").asString();
        String presupuestos = mockMvc.perform(get(RUTA_PRESUPUESTOS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // Toda persona recibe "Mi presupuesto" al registrarse: es el único de la lista.
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    private ResultActions crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return enviar(post(RUTA_PRESUPUESTOS), sesion, Map.of("nombre", nombre));
    }

    /** El presupuesto más reciente de la lista (la lista viene ordenada por nombre). */
    private long ultimoPresupuesto(Sesion sesion) throws Exception {
        String cuerpo = mockMvc.perform(get(RUTA_PRESUPUESTOS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (var presupuesto : objectMapper.readTree(cuerpo)) {
            if (presupuesto.get("id").asLong() != sesion.presupuestoId) {
                return presupuesto.get("id").asLong();
            }
        }
        throw new IllegalStateException("No hay un segundo presupuesto");
    }

    private long crearCategoria(Sesion sesion, long presupuestoId, String nombre)
            throws Exception {
        long grupo = idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/grupos-categorias"),
                sesion, Map.of("nombre", "Grupo " + nombre)));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupo);
        cuerpo.put("nombre", nombre);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias"),
                sesion, cuerpo));
    }

    private ResultActions accion(
            Sesion sesion, long presupuestoId, String recurso, long id, String accion)
            throws Exception {
        return mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/" + recurso
                        + "/" + id + "/" + accion)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions crear(Sesion sesion, long presupuestoId, String nombre, Long categoriaId)
            throws Exception {
        return enviar(post(ruta(presupuestoId)), sesion, cuerpo(nombre, categoriaId));
    }

    private ResultActions listar(Sesion sesion, long presupuestoId, String q, Integer limite)
            throws Exception {
        MockHttpServletRequestBuilder peticion = get(ruta(presupuestoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token));
        if (q != null) {
            peticion.param("q", q);
        }
        if (limite != null) {
            peticion.param("limite", String.valueOf(limite));
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions obtener(Sesion sesion, long presupuestoId, long id) throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions editar(
            Sesion sesion, long presupuestoId, long id, Map<String, Object> cuerpo)
            throws Exception {
        return enviar(put(ruta(presupuestoId) + "/" + id), sesion, cuerpo);
    }

    private ResultActions enviar(
            MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private static Map<String, Object> cuerpo(String nombre, Long categoriaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return cuerpo;
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/beneficiarios";
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
