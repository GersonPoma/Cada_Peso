package com.presupuesto.reporte.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Apoyo HTTP de los tests de integración de reportes: registra personas y arma datos (cuentas,
 * categorías, transacciones, divisiones, transferencias, metas) por la API, como lo haría un
 * cliente, y consulta los reportes con el token.
 */
final class ApoyoHttpReporte {

    static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    /** Persona registrada y su presupuesto inicial. */
    record Sesion(String token, long presupuestoId) {}

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    ApoyoHttpReporte(MockMvc mockMvc, ObjectMapper objectMapper) {
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

    long crearGrupo(Sesion sesion, String nombre) throws Exception {
        return idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/grupos-categorias"),
                sesion, Map.of("nombre", nombre)));
    }

    long crearCategoria(Sesion sesion, long grupoId, String nombre) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupoId);
        cuerpo.put("nombre", nombre);
        return idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/categorias"),
                sesion, cuerpo));
    }

    /** Crea un grupo propio con la categoría dentro. */
    long crearCategoria(Sesion sesion, String nombre) throws Exception {
        return crearCategoria(sesion, crearGrupo(sesion, "Grupo " + nombre), nombre);
    }

    void ocultarCategoria(Sesion sesion, long categoriaId) throws Exception {
        accion(sesion, RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/categorias/"
                + categoriaId + "/ocultar").andExpect(status().isOk());
    }

    long crearCuenta(
            Sesion sesion, String nombre, String tipo, boolean enPresupuesto, long saldoInicial)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", tipo);
        cuerpo.put("enPresupuesto", enPresupuesto);
        cuerpo.put("saldoInicial", saldoInicial);
        return idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/cuentas"),
                sesion, cuerpo));
    }

    void cerrarCuenta(Sesion sesion, long cuentaId) throws Exception {
        accion(sesion, RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/cuentas/"
                + cuentaId + "/cerrar").andExpect(status().isOk());
    }

    /** {@code categoriaId} puede ser {@code null} (sin categoría). */
    long transaccion(
            Sesion sesion, long cuentaId, String fecha, long monto, Long categoriaId)
            throws Exception {
        return idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/transacciones"),
                sesion, cuerpoTransaccion(cuentaId, fecha, monto, categoriaId, null)));
    }

    /** Una transacción dividida; cada parte es {@code {categoriaId o null, monto}}. */
    long division(Sesion sesion, long cuentaId, String fecha, long monto, List<Long[]> partes)
            throws Exception {
        List<Map<String, Object>> subtransacciones = new ArrayList<>();
        for (Long[] parte : partes) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("categoriaId", parte[0]);
            sub.put("monto", parte[1]);
            subtransacciones.add(sub);
        }
        return idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/transacciones"),
                sesion, cuerpoTransaccion(cuentaId, fecha, monto, null, subtransacciones)));
    }

    /** Transferencia de {@code monto} (valor absoluto); devuelve el cuerpo de la respuesta. */
    JsonNode transferencia(
            Sesion sesion, long origenId, long destinoId, String fecha, long monto,
            Long categoriaId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaOrigenId", origenId);
        cuerpo.put("cuentaDestinoId", destinoId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        cuerpo.put("categoriaId", categoriaId);
        return cuerpo(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/transferencias"),
                sesion, cuerpo).andExpect(status().isCreated()));
    }

    /** El id de la categoría de pago de la tarjeta, según el mes de {@code asignacion}. */
    long categoriaDePago(Sesion sesion, long cuentaId) throws Exception {
        JsonNode mes = cuerpo(consultar(sesion, rutaMes(sesion.presupuestoId(), "2026-10"),
                "incluirOcultas", "true").andExpect(status().isOk()));
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()
                        && categoria.get("cuentaId").asLong() == cuentaId) {
                    return categoria.get("categoriaId").asLong();
                }
            }
        }
        throw new AssertionError("La tarjeta " + cuentaId + " no tiene categoría de pago");
    }

    /** El saldo de cada cuenta según el listado de saldos de {@code transacciones}. */
    Map<Long, Long> saldos(Sesion sesion) throws Exception {
        JsonNode lista = cuerpo(consultar(sesion, RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId()
                + "/transacciones/saldos").andExpect(status().isOk()));
        Map<Long, Long> saldos = new LinkedHashMap<>();
        for (JsonNode saldo : lista) {
            saldos.put(saldo.get("cuentaId").asLong(), saldo.get("saldo").asLong());
        }
        return saldos;
    }

    /** Fija el asignado de la categoría en el mes con la ruta de {@code asignacion}. */
    void asignar(Sesion sesion, String mes, long categoriaId, long asignado) throws Exception {
        enviar(put(rutaMes(sesion.presupuestoId(), mes) + "/categorias/" + categoriaId), sesion,
                Map.of("asignado", asignado)).andExpect(status().isOk());
    }

    void guardarMeta(Sesion sesion, long categoriaId, Map<String, Object> meta)
            throws Exception {
        enviar(put(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId() + "/categorias/"
                + categoriaId + "/meta"), sesion, meta).andExpect(status().isOk());
    }

    void posponerMeta(Sesion sesion, String mes, long categoriaId) throws Exception {
        accion(sesion, rutaMes(sesion.presupuestoId(), mes) + "/metas/" + categoriaId
                + "/posponer").andExpect(status().isOk());
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

    static Map<String, Object> paraFecha(long monto, String objetivo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tipo", "MONTO_PARA_FECHA");
        cuerpo.put("monto", monto);
        cuerpo.put("fechaObjetivo", objetivo);
        return cuerpo;
    }

    /** {@code GET} de un reporte de la sesión con los parámetros dados (pares nombre, valor). */
    ResultActions reporte(Sesion sesion, String ruta, String... parametros) throws Exception {
        return reporte(sesion, sesion.presupuestoId(), ruta, parametros);
    }

    ResultActions reporte(Sesion sesion, long presupuestoId, String ruta, String... parametros)
            throws Exception {
        MockHttpServletRequestBuilder peticion = get(
                RUTA_PRESUPUESTOS + "/" + presupuestoId + "/reportes/" + ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token()));
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mockMvc.perform(peticion);
    }

    /** El cuerpo de un reporte pedido con éxito. */
    JsonNode reporteOk(Sesion sesion, String ruta, String... parametros) throws Exception {
        return cuerpo(reporte(sesion, ruta, parametros).andExpect(status().isOk()));
    }

    ResultActions consultar(Sesion sesion, String ruta) throws Exception {
        return mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    ResultActions consultar(Sesion sesion, String ruta, String parametro, String valor)
            throws Exception {
        return mockMvc.perform(get(ruta).param(parametro, valor)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token())));
    }

    ResultActions enviar(MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
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

    static String rutaMes(long presupuestoId, String mes) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/meses/" + mes;
    }

    static String bearer(String token) {
        return "Bearer " + token;
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

    static void esperarDatosInvalidos(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    private static Map<String, Object> cuerpoTransaccion(
            long cuentaId, String fecha, long monto, Long categoriaId,
            List<Map<String, Object>> subtransacciones) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        cuerpo.put("categoriaId", categoriaId);
        if (subtransacciones != null) {
            cuerpo.put("subtransacciones", subtransacciones);
        }
        return cuerpo;
    }
}
