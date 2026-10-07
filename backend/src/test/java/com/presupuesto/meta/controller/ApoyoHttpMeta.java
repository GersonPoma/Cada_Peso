package com.presupuesto.meta.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Apoyo HTTP de los tests de integración de metas: registra personas, crea presupuestos,
 * categorías, cuentas y transacciones por la API y arma las peticiones con el token.
 */
final class ApoyoHttpMeta {

    static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    /** Persona registrada y su presupuesto inicial. */
    record Sesion(String token, long presupuestoId) {}

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    ApoyoHttpMeta(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    Sesion registrar(String email) throws Exception {
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

    long crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return idDe(enviar(post(RUTA_PRESUPUESTOS), sesion, Map.of("nombre", nombre)));
    }

    /** Crea un grupo nuevo (queda al final del árbol) con la categoría dentro. */
    long crearCategoria(Sesion sesion, long presupuestoId, String nombre) throws Exception {
        long grupo = idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/grupos-categorias"),
                sesion, Map.of("nombre", "Grupo " + nombre)));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupo);
        cuerpo.put("nombre", nombre);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias"),
                sesion, cuerpo));
    }

    void ocultarCategoria(Sesion sesion, long presupuestoId, long categoriaId) throws Exception {
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias/"
                        + categoriaId + "/ocultar")
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())))
                .andExpect(status().isOk());
    }

    long crearCuenta(Sesion sesion, long presupuestoId, long saldoInicial) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Banco");
        cuerpo.put("tipo", "CORRIENTE");
        cuerpo.put("enPresupuesto", true);
        cuerpo.put("saldoInicial", saldoInicial);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"),
                sesion, cuerpo));
    }

    void crearTransaccion(
            Sesion sesion, long presupuestoId, long cuentaId, String fecha, long monto,
            long categoriaId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        cuerpo.put("categoriaId", categoriaId);
        enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transacciones"), sesion, cuerpo)
                .andExpect(status().isCreated());
    }

    /** Fija el asignado de la categoría en el mes con la ruta de {@code asignacion}. */
    void asignar(Sesion sesion, long presupuestoId, String mes, long categoriaId, long asignado)
            throws Exception {
        enviar(put(rutaMes(presupuestoId, mes) + "/categorias/" + categoriaId), sesion,
                Map.of("asignado", asignado))
                .andExpect(status().isOk());
    }

    /** El asignado del mes de una categoría según {@code GET /meses/{mes}}. */
    long asignadoDe(Sesion sesion, long presupuestoId, String mes, long categoriaId)
            throws Exception {
        String cuerpo = mockMvc.perform(get(rutaMes(presupuestoId, mes))
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode grupo : objectMapper.readTree(cuerpo).get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("categoriaId").asLong() == categoriaId) {
                    return categoria.get("asignado").asLong();
                }
            }
        }
        throw new AssertionError("La categoría " + categoriaId + " no está en el mes");
    }

    /** El {@code listoParaAsignar} del mes según {@code GET /meses/{mes}}. */
    long listoParaAsignar(Sesion sesion, long presupuestoId, String mes) throws Exception {
        String cuerpo = mockMvc.perform(get(rutaMes(presupuestoId, mes))
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("listoParaAsignar").asLong();
    }

    ResultActions enviar(MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    ResultActions consultar(Sesion sesion, String ruta) throws Exception {
        return mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    ResultActions consultar(Sesion sesion, String ruta, String parametro, String valor)
            throws Exception {
        return mockMvc.perform(get(ruta).param(parametro, valor)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    ResultActions borrar(Sesion sesion, String ruta) throws Exception {
        return mockMvc.perform(
                delete(ruta).header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    ResultActions accion(Sesion sesion, String ruta) throws Exception {
        return mockMvc.perform(
                post(ruta).header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    JsonNode cuerpo(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    long idDe(ResultActions creacion) throws Exception {
        return cuerpo(creacion.andExpect(status().isCreated())).get("id").asLong();
    }

    static String rutaMeta(long presupuestoId, long categoriaId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias/" + categoriaId + "/meta";
    }

    static String rutaMetas(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/metas";
    }

    static String rutaMes(long presupuestoId, String mes) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/meses/" + mes;
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static Map<String, Object> metaMensual(long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "MONTO_MENSUAL");
        cuerpo.put("monto", monto);
        cuerpo.put("frecuencia", "MENSUAL");
        return cuerpo;
    }

    static Map<String, Object> saldoObjetivo(long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "SALDO_OBJETIVO");
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    static void esperarNoEncontrado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    static void esperarDatosInvalidos(ResultActions resultado, String campo) throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores['" + campo + "']", notNullValue()));
    }

    static void esperarDatosInvalidosSinCampos(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }
}
