package com.presupuesto.presupuesto.controller;

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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresupuestoIntegracionTest {

    private static final String RUTA = "/api/v1/presupuestos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        esperarNoAutenticado(mockMvc.perform(get(RUTA)));
        esperarNoAutenticado(mockMvc.perform(get(RUTA + "/1")));
        esperarNoAutenticado(mockMvc.perform(post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"Casa\"}")));
        esperarNoAutenticado(mockMvc.perform(put(RUTA + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"Casa\"}")));
    }

    @Test
    void crearConMonedaExplicitaDevuelve201() throws Exception {
        String token = registrar("ana@ejemplo.com", null);

        crear(token, "Viajes", "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nombre").value("Viajes"))
                .andExpect(jsonPath("$.moneda").value("USD"))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
    }

    @Test
    void crearSinMonedaUsaLaMonedaPredeterminadaDelPerfil() throws Exception {
        String token = registrar("ana@ejemplo.com", "USD");

        crear(token, "Viajes", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moneda").value("USD"));
    }

    @Test
    void elNombreSeGuardaRecortado() throws Exception {
        String token = registrar("ana@ejemplo.com", null);

        crear(token, "  Casa  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Casa"));
    }

    @Test
    void unNombreRepetidoSinDistinguirMayusculasDevuelve409() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        crear(token, "Casa", null).andExpect(status().isCreated());

        crear(token, " CASA ", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_YA_EXISTE"));

        listar(token).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void elMismoNombreEsValidoEnUsuariosDistintos() throws Exception {
        String tokenAna = registrar("ana@ejemplo.com", null);
        String tokenBeto = registrar("beto@ejemplo.com", null);
        crear(tokenAna, "Casa", null).andExpect(status().isCreated());

        crear(tokenBeto, "Casa", null).andExpect(status().isCreated());
    }

    @Test
    void unNombreVacioODemasiadoLargoDevuelve400() throws Exception {
        String token = registrar("ana@ejemplo.com", null);

        esperarDatosInvalidos(crear(token, "   ", null), "nombre");
        esperarDatosInvalidos(crear(token, "a".repeat(101), null), "nombre");
    }

    @Test
    void unaMonedaInexistenteOEnMinusculasDevuelve400() throws Exception {
        String token = registrar("ana@ejemplo.com", null);

        esperarDatosInvalidos(crear(token, "Casa", "ZZZ"), "moneda");
        esperarDatosInvalidos(crear(token, "Casa", "usd"), "moneda");
    }

    @Test
    void laListaEsCompletaPropiaYOrdenadaPorNombre() throws Exception {
        String tokenAna = registrar("ana@ejemplo.com", null);
        String tokenBeto = registrar("beto@ejemplo.com", null);
        crear(tokenAna, "Viajes", null).andExpect(status().isCreated());
        crear(tokenAna, "casa", null).andExpect(status().isCreated());
        crear(tokenBeto, "Otro", null).andExpect(status().isCreated());

        // Ana tiene además su "Mi presupuesto" inicial.
        listar(tokenAna)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nombre").value("casa"))
                .andExpect(jsonPath("$[1].nombre").value("Mi presupuesto"))
                .andExpect(jsonPath("$[2].nombre").value("Viajes"));
    }

    @Test
    void elDetalleDeUnPresupuestoPropioDevuelve200() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "Casa", "USD"));

        mockMvc.perform(get(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Casa"))
                .andExpect(jsonPath("$.moneda").value("USD"));
    }

    @Test
    void renombrarDevuelve200ConElNuevoNombre() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "Casa", null));

        renombrar(token, id, "Hogar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hogar"));
    }

    @Test
    void cambiarSoloLasMayusculasDelPropioNombreEsValido() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "casa", null));

        renombrar(token, id, "Casa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Casa"));
    }

    @Test
    void renombrarAlNombreDeOtroPresupuestoPropioDevuelve409() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        crear(token, "Casa", null).andExpect(status().isCreated());
        long viajes = idDe(crear(token, "Viajes", null));

        renombrar(token, viajes, "casa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_YA_EXISTE"));
    }

    @Test
    void renombrarConUnNombreInvalidoDevuelve400() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "Casa", null));

        esperarDatosInvalidos(renombrar(token, id, "  "), "nombre");
    }

    @Test
    void laMonedaNoSeEditaAunqueElPutLaEnvie() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "Casa", "BOB"));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Hogar");
        cuerpo.put("moneda", "USD");

        mockMvc.perform(put(RUTA + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hogar"))
                .andExpect(jsonPath("$.moneda").value("BOB"));

        mockMvc.perform(get(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.moneda").value("BOB"));
    }

    @Test
    void elPresupuestoDeOtraPersonaDevuelve404EnVezDe403() throws Exception {
        String tokenAna = registrar("ana@ejemplo.com", null);
        String tokenBeto = registrar("beto@ejemplo.com", null);
        long idDeAna = idDe(crear(tokenAna, "Casa", null));

        mockMvc.perform(get(RUTA + "/" + idDeAna)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenBeto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        renombrar(tokenBeto, idDeAna, "Mio")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mockMvc.perform(get(RUTA + "/" + idDeAna)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAna)))
                .andExpect(jsonPath("$.nombre").value("Casa"));
    }

    @Test
    void unPresupuestoInexistenteDevuelve404() throws Exception {
        String token = registrar("ana@ejemplo.com", null);

        mockMvc.perform(get(RUTA + "/" + Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        renombrar(token, Long.MAX_VALUE, "Casa")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void noExisteElBorrado() throws Exception {
        String token = registrar("ana@ejemplo.com", null);
        long id = idDe(crear(token, "Casa", null));

        mockMvc.perform(delete(RUTA + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isMethodNotAllowed());
    }

    private String registrar(String email, String moneda) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", email);
        datos.put("contrasena", "secreta123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        if (moneda != null) {
            datos.put("monedaPredeterminada", moneda);
        }
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private ResultActions crear(String token, String nombre, String moneda) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        if (moneda != null) {
            cuerpo.put("moneda", moneda);
        }
        return mockMvc.perform(post(RUTA)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions renombrar(String token, long id, String nombre) throws Exception {
        return mockMvc.perform(put(RUTA + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nombre", nombre))));
    }

    private ResultActions listar(String token) throws Exception {
        return mockMvc.perform(get(RUTA).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    private static void esperarDatosInvalidos(ResultActions resultado, String campo)
            throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }
}
