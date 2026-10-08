package com.presupuesto.transaccionprogramada.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Ayudas HTTP compartidas por los tests de integración de transacciones programadas. */
class ClienteProgramadas {

    static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    record Sesion(String token, long presupuestoId) {}

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    ClienteProgramadas(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transacciones-programadas";
    }

    private static String bearer(Sesion sesion) {
        return "Bearer " + sesion.token();
    }

    /** Registra a una persona; su único presupuesto es el inicial. */
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    long crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return idDe(enviar(post(RUTA_PRESUPUESTOS), sesion, Map.of("nombre", nombre)));
    }

    long crearCuenta(Sesion sesion, long presupuestoId, String nombre) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", "CORRIENTE");
        cuerpo.put("saldoInicial", 0);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"),
                sesion, cuerpo));
    }

    void cerrarCuenta(Sesion sesion, long presupuestoId, long cuentaId) throws Exception {
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas/" + cuentaId
                        + "/cerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion)))
                .andExpect(status().isOk());
    }

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

    /** Crea una tarjeta de crédito y devuelve el id de su categoría de pago. */
    long crearCategoriaDePago(Sesion sesion, long presupuestoId, String tarjeta)
            throws Exception {
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("nombre", tarjeta);
        cuenta.put("tipo", "TARJETA_CREDITO");
        idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"), sesion, cuenta));
        String arbol = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias")
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode grupo : objectMapper.readTree(arbol)) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()
                        && categoria.get("nombre").asString().equals("Pago: " + tarjeta)) {
                    return categoria.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No hay categoría de pago de " + tarjeta);
    }

    /** Cuerpo mínimo de una plantilla mensual; la prueba lo ajusta. */
    static Map<String, Object> cuerpo(long cuentaId, String inicio, String frecuencia, long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fechaInicio", inicio);
        cuerpo.put("frecuencia", frecuencia);
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    /** Cuerpo de edición con los campos obligatorios. */
    static Map<String, Object> edicion(String frecuencia, long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("frecuencia", frecuencia);
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    ResultActions enviar(MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    ResultActions sinCuerpo(MockHttpServletRequestBuilder peticion, Sesion sesion)
            throws Exception {
        return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(sesion)));
    }

    ResultActions crear(Sesion sesion, long presupuestoId, Map<String, Object> cuerpo)
            throws Exception {
        return enviar(post(ruta(presupuestoId)), sesion, cuerpo);
    }

    long crearYObtenerId(Sesion sesion, long presupuestoId, Map<String, Object> cuerpo)
            throws Exception {
        return idDe(crear(sesion, presupuestoId, cuerpo));
    }

    ResultActions listar(Sesion sesion, long presupuestoId) throws Exception {
        return sinCuerpo(get(ruta(presupuestoId)), sesion);
    }

    ResultActions soloActivas(Sesion sesion, long presupuestoId) throws Exception {
        return sinCuerpo(get(ruta(presupuestoId)).param("soloActivas", "true"), sesion);
    }

    ResultActions obtener(Sesion sesion, long presupuestoId, long id) throws Exception {
        return sinCuerpo(get(ruta(presupuestoId) + "/" + id), sesion);
    }

    ResultActions editar(Sesion sesion, long presupuestoId, long id, Map<String, Object> cuerpo)
            throws Exception {
        return enviar(put(ruta(presupuestoId) + "/" + id), sesion, cuerpo);
    }

    ResultActions borrar(Sesion sesion, long presupuestoId, long id) throws Exception {
        return sinCuerpo(delete(ruta(presupuestoId) + "/" + id), sesion);
    }

    ResultActions pausar(Sesion sesion, long presupuestoId, long id) throws Exception {
        return sinCuerpo(post(ruta(presupuestoId) + "/" + id + "/pausar"), sesion);
    }

    ResultActions reanudar(Sesion sesion, long presupuestoId, long id) throws Exception {
        return sinCuerpo(post(ruta(presupuestoId) + "/" + id + "/reanudar"), sesion);
    }

    ResultActions generar(Sesion sesion, long presupuestoId) throws Exception {
        return sinCuerpo(post(ruta(presupuestoId) + "/generar"), sesion);
    }

    /** Todas las transacciones del presupuesto (hasta 100), de la API de transacciones. */
    JsonNode transacciones(Sesion sesion, long presupuestoId) throws Exception {
        String cuerpo = sinCuerpo(get(RUTA_PRESUPUESTOS + "/" + presupuestoId
                        + "/transacciones").param("size", "100"), sesion)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    ResultActions transaccion(Sesion sesion, long presupuestoId, long id) throws Exception {
        return sinCuerpo(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transacciones/" + id),
                sesion);
    }

    long idDe(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }
}
