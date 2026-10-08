package com.presupuesto.categoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Categorías de pago de tarjeta de punta a punta: cada test crea sus propios datos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagosTarjetaIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String NOMBRE_GRUPO = "Pagos de tarjetas de crédito";
    private static final int GRUPOS_INICIALES = 4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- ciclo de vida de la tarjeta ----------

    @Test
    void unPresupuestoNuevoNoTieneElGrupoDePagos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        JsonNode arbol = arbol(ana, true);

        assertThat(arbol.size()).isEqualTo(GRUPOS_INICIALES);
        for (JsonNode grupo : arbol) {
            assertThat(grupo.get("tipo").asString()).isEqualTo("NORMAL");
            for (JsonNode categoria : grupo.get("categorias")) {
                assertThat(categoria.get("esPagoTarjeta").asBoolean()).isFalse();
                assertThat(categoria.get("cuentaId").isNull()).isTrue();
            }
        }
    }

    @Test
    void crearUnaTarjetaCreaElGrupoAlFinalYSuCategoriaDePago() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        long visa = idDe(crearCuenta(ana, "Visa", "TARJETA_CREDITO", null));

        JsonNode arbol = arbol(ana, true);
        assertThat(arbol.size()).isEqualTo(GRUPOS_INICIALES + 1);
        JsonNode pagos = arbol.get(GRUPOS_INICIALES);
        assertThat(pagos.get("nombre").asString()).isEqualTo(NOMBRE_GRUPO);
        assertThat(pagos.get("tipo").asString()).isEqualTo("PAGOS_TARJETA");
        assertThat(pagos.get("categorias").size()).isEqualTo(1);
        JsonNode categoria = pagos.get("categorias").get(0);
        assertThat(categoria.get("nombre").asString()).isEqualTo("Pago: Visa");
        assertThat(categoria.get("esPagoTarjeta").asBoolean()).isTrue();
        assertThat(categoria.get("cuentaId").asLong()).isEqualTo(visa);
        assertThat(categoria.get("oculta").asBoolean()).isFalse();
    }

    @Test
    void unaSegundaTarjetaReutilizaElGrupo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        crearCuenta(ana, "Master", "TARJETA_CREDITO", null).andExpect(status().isCreated());

        JsonNode arbol = arbol(ana, true);

        assertThat(arbol.size()).isEqualTo(GRUPOS_INICIALES + 1);
        assertThat(arbol.get(GRUPOS_INICIALES).get("categorias").size()).isEqualTo(2);
    }

    @Test
    void unaTarjetaDeSeguimientoYLasOtrasCuentasNoCreanNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crearCuenta(ana, "Visa externa", "TARJETA_CREDITO", false).andExpect(status().isCreated());
        crearCuenta(ana, "Banco", "CORRIENTE", null).andExpect(status().isCreated());

        assertThat(arbol(ana, true).size()).isEqualTo(GRUPOS_INICIALES);
    }

    @Test
    void renombrarLaTarjetaRenombraLaCategoria() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = idDe(crearCuenta(ana, "Visa", "TARJETA_CREDITO", null));
        long categoriaId = categoriaDePago(ana).get("id").asLong();

        editarCuenta(ana, visa, "Visa Oro").andExpect(status().isOk());

        JsonNode categoria = categoriaDePago(ana);
        assertThat(categoria.get("id").asLong()).isEqualTo(categoriaId);
        assertThat(categoria.get("nombre").asString()).isEqualTo("Pago: Visa Oro");
    }

    @Test
    void renombrarSoloCambiandoMayusculasDejaLaCategoriaSinSufijo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = idDe(crearCuenta(ana, "Visa", "TARJETA_CREDITO", null));

        editarCuenta(ana, visa, "VISA").andExpect(status().isOk());

        assertThat(categoriaDePago(ana).get("nombre").asString()).isEqualTo("Pago: VISA");
    }

    @Test
    void cerrarLaTarjetaOcultaLaCategoriaYReabrirlaLaMuestra() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = idDe(crearCuenta(ana, "Visa", "TARJETA_CREDITO", null));

        accionCuenta(ana, visa, "cerrar").andExpect(status().isOk());
        assertThat(categoriaDePago(ana).get("oculta").asBoolean()).isTrue();
        JsonNode sinOcultas = arbol(ana, false);
        assertThat(sinOcultas.get(GRUPOS_INICIALES).get("categorias").size()).isZero();

        accionCuenta(ana, visa, "reabrir").andExpect(status().isOk());
        assertThat(categoriaDePago(ana).get("oculta").asBoolean()).isFalse();
        assertThat(arbol(ana, false).get(GRUPOS_INICIALES).get("categorias").size()).isEqualTo(1);
    }

    @Test
    void unNombreRepetidoSoloEnMayusculasDevuelve409YNoCreaOtraCategoriaNiOtroGrupo()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());

        crearCuenta(ana, "visa", "TARJETA_CREDITO", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_YA_EXISTE"));

        JsonNode arbol = arbol(ana, true);
        assertThat(arbol.size()).isEqualTo(GRUPOS_INICIALES + 1);
        JsonNode pagos = arbol.get(GRUPOS_INICIALES);
        assertThat(pagos.get("categorias").size()).isEqualTo(1);
        assertThat(pagos.get("categorias").get(0).get("nombre").asString())
                .isEqualTo("Pago: Visa");
    }

    @Test
    void conUnGrupoNormalConElNombreDelGrupoDePagosLaTarjetaSeCreaConNombreAlterno()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long normal = idDe(enviar(ana, rutaPresupuesto(ana) + "/grupos-categorias",
                Map.of("nombre", NOMBRE_GRUPO)));
        crear(ana, normal, "Mercado").andExpect(status().isCreated());

        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());

        JsonNode arbol = arbol(ana, true);
        assertThat(arbol.size()).isEqualTo(GRUPOS_INICIALES + 2);
        JsonNode grupoNormal = arbol.get(GRUPOS_INICIALES);
        assertThat(grupoNormal.get("nombre").asString()).isEqualTo(NOMBRE_GRUPO);
        assertThat(grupoNormal.get("tipo").asString()).isEqualTo("NORMAL");
        assertThat(grupoNormal.get("categorias").size()).isEqualTo(1);
        JsonNode pagos = arbol.get(GRUPOS_INICIALES + 1);
        assertThat(pagos.get("nombre").asString()).isEqualTo(NOMBRE_GRUPO + " (2)");
        assertThat(pagos.get("tipo").asString()).isEqualTo("PAGOS_TARJETA");

        crearCuenta(ana, "Master", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        JsonNode despues = arbol(ana, true);
        assertThat(despues.size()).isEqualTo(GRUPOS_INICIALES + 2);
        assertThat(despues.get(GRUPOS_INICIALES + 1).get("categorias").size()).isEqualTo(2);
    }

    @Test
    void unaTarjetaConNombreDe100CaracteresSeCreaYSeRenombraConCategoriaDeA100() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        long id = idDe(crearCuenta(ana, "a".repeat(100), "TARJETA_CREDITO", null));
        assertThat(categoriaDePago(ana).get("nombre").asString())
                .hasSize(100).startsWith("Pago: aaa");

        editarCuenta(ana, id, "b".repeat(100)).andExpect(status().isOk());
        assertThat(categoriaDePago(ana).get("nombre").asString())
                .hasSize(100).startsWith("Pago: bbb");
    }

    // ---------- protecciones ----------

    @Test
    void laCategoriaDePagoNoSeEditaOcultaMuestraNiMueve() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        JsonNode pago = categoriaDePago(ana);
        long id = pago.get("id").asLong();
        long primerGrupo = arbol(ana, true).get(0).get("id").asLong();
        Map<String, Object> edicion = new LinkedHashMap<>();
        edicion.put("nombre", "Otro nombre");
        edicion.put("nota", "Una nota");

        esperarRegla(mockMvc.perform(put(rutaCategorias(ana) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(edicion))));
        esperarRegla(accionCategoria(ana, id, "ocultar"));
        esperarRegla(accionCategoria(ana, id, "mostrar"));
        esperarRegla(enviar(ana, rutaCategorias(ana) + "/" + id + "/mover",
                Map.of("grupoId", primerGrupo, "posicion", 0)));
        esperarRegla(enviar(ana, rutaCategorias(ana) + "/" + id + "/mover",
                Map.of("grupoId", pago.get("grupoId").asLong(), "posicion", 0)));

        JsonNode igual = categoriaDePago(ana);
        assertThat(igual.get("nombre").asString()).isEqualTo("Pago: Visa");
        assertThat(igual.get("nota").isNull()).isTrue();
        assertThat(igual.get("oculta").asBoolean()).isFalse();
        assertThat(igual.get("grupoId").asLong()).isEqualTo(pago.get("grupoId").asLong());
    }

    @Test
    void noSeCreanNiSeMuevenCategoriasAlGrupoDePagos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        long grupoPagos = arbol(ana, true).get(GRUPOS_INICIALES).get("id").asLong();
        JsonNode primerGrupo = arbol(ana, true).get(0);
        long comida = primerGrupo.get("categorias").get(0).get("id").asLong();

        esperarRegla(crear(ana, grupoPagos, "Mi pago"));
        esperarRegla(enviar(ana, rutaCategorias(ana) + "/" + comida + "/mover",
                Map.of("grupoId", grupoPagos, "posicion", 0)));

        JsonNode arbol = arbol(ana, true);
        assertThat(arbol.get(GRUPOS_INICIALES).get("categorias").size()).isEqualTo(1);
        assertThat(arbol.get(0).get("categorias").get(0).get("id").asLong()).isEqualTo(comida);
    }

    @Test
    void elGrupoDePagosNoSeRenombraOcultaMuestraNiMueve() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        long grupoPagos = arbol(ana, true).get(GRUPOS_INICIALES).get("id").asLong();
        String ruta = rutaPresupuesto(ana) + "/grupos-categorias/" + grupoPagos;

        esperarRegla(mockMvc.perform(put(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nombre", "Otro")))));
        esperarRegla(mockMvc.perform(post(ruta + "/ocultar")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarRegla(mockMvc.perform(post(ruta + "/mostrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarRegla(enviar(ana, ruta + "/mover", Map.of("posicion", 0)));

        JsonNode pagos = arbol(ana, true).get(GRUPOS_INICIALES);
        assertThat(pagos.get("nombre").asString()).isEqualTo(NOMBRE_GRUPO);
        assertThat(pagos.get("oculto").asBoolean()).isFalse();
    }

    @Test
    void losDemasGruposSiguenMoviendoseConElGrupoDePagosAlFinal() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        long primero = arbol(ana, true).get(0).get("id").asLong();

        enviar(ana, rutaPresupuesto(ana) + "/grupos-categorias/" + primero + "/mover",
                Map.of("posicion", 2)).andExpect(status().isOk());

        JsonNode arbol = arbol(ana, true);
        for (int i = 0; i < arbol.size(); i++) {
            assertThat(arbol.get(i).get("orden").asInt()).isEqualTo(i);
        }
        assertThat(arbol.get(2).get("id").asLong()).isEqualTo(primero);
    }

    @Test
    void laCategoriaDePagoDeOtroPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        crearCuenta(ana, "Visa", "TARJETA_CREDITO", null).andExpect(status().isCreated());
        long deAna = categoriaDePago(ana).get("id").asLong();

        accionCategoria(beto, deAna, "ocultar")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        assertThat(categoriaDePago(ana).get("oculta").asBoolean()).isFalse();
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
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    private ResultActions crearCuenta(Sesion sesion, String nombre, String tipo,
            Boolean enPresupuesto) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", tipo);
        if (enPresupuesto != null) {
            cuerpo.put("enPresupuesto", enPresupuesto);
        }
        return enviar(sesion, rutaPresupuesto(sesion) + "/cuentas", cuerpo);
    }

    private ResultActions editarCuenta(Sesion sesion, long id, String nombre) throws Exception {
        return mockMvc.perform(put(rutaPresupuesto(sesion) + "/cuentas/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("nombre", nombre, "tipo", "TARJETA_CREDITO"))));
    }

    private ResultActions accionCuenta(Sesion sesion, long id, String accion) throws Exception {
        return mockMvc.perform(post(rutaPresupuesto(sesion) + "/cuentas/" + id + "/" + accion)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions accionCategoria(Sesion sesion, long id, String accion)
            throws Exception {
        return mockMvc.perform(post(rutaCategorias(sesion) + "/" + id + "/" + accion)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions crear(Sesion sesion, long grupoId, String nombre) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupoId);
        cuerpo.put("nombre", nombre);
        return enviar(sesion, rutaCategorias(sesion), cuerpo);
    }

    private JsonNode arbol(Sesion sesion, boolean incluirOcultas) throws Exception {
        String cuerpo = mockMvc.perform(get(rutaCategorias(sesion))
                        .param("incluirOcultas", String.valueOf(incluirOcultas))
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    /** La primera categoría de pago del árbol (con ocultas), por orden de grupo. */
    private JsonNode categoriaDePago(Sesion sesion) throws Exception {
        for (JsonNode grupo : arbol(sesion, true)) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()) {
                    return categoria;
                }
            }
        }
        throw new AssertionError("No hay categoría de pago de tarjeta");
    }

    private ResultActions enviar(Sesion sesion, String ruta, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(post(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String rutaPresupuesto(Sesion sesion) {
        return RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId;
    }

    private static String rutaCategorias(Sesion sesion) {
        return rutaPresupuesto(sesion) + "/categorias";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void esperarRegla(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));
    }
}
