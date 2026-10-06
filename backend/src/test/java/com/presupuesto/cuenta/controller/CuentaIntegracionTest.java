package com.presupuesto.cuenta.controller;

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
class CuentaIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{\"nombre\": \"Banco\", \"tipo\": \"CORRIENTE\"}";

        esperarNoAutenticado(mockMvc.perform(get(ruta)));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/cerrar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/reabrir")));
    }

    // ---------- creación y validación ----------

    @Test
    void crearConValoresPorDefectoDevuelve201() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Efectivo", "EFECTIVO", null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.nombre").value("Efectivo"))
                .andExpect(jsonPath("$.tipo").value("EFECTIVO"))
                .andExpect(jsonPath("$.enPresupuesto").value(true))
                .andExpect(jsonPath("$.saldoInicial").value(0))
                .andExpect(jsonPath("$.cerrada").value(false))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
    }

    @Test
    void crearCuentaDeSeguimientoConSaldoDevuelve201() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Fondo", "INVERSION", false, 5_000_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enPresupuesto").value(false))
                .andExpect(jsonPath("$.saldoInicial").value(5_000_000));
    }

    @Test
    void elNombreSeGuardaRecortado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "  Banco  ", "CORRIENTE", null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Banco"));
    }

    @Test
    void unNombreVacioODemasiadoLargoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, "   ", "CORRIENTE", null, null),
                "nombre");
        esperarDatosInvalidos(
                crear(ana, ana.presupuestoId, "a".repeat(101), "CORRIENTE", null, null),
                "nombre");
    }

    @Test
    void unTipoDesconocidoDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Banco", "OTRO", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void unTipoAusenteDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Banco", null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores.tipo", notNullValue()));
    }

    // ---------- saldo negativo por tipo ----------

    @Test
    void elSaldoNegativoSeAdmiteEnTarjetaDeCreditoYPrestamo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Tarjeta", "TARJETA_CREDITO", null, -250_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoInicial").value(-250_000));
        crear(ana, ana.presupuestoId, "Deuda", "PRESTAMO", null, -1_000_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoInicial").value(-1_000_000));
    }

    @Test
    void elSaldoNegativoEnOtroTipoDevuelve422YNoCreaLaCuenta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, -1L)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));

        listar(ana, ana.presupuestoId, true).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- duplicados ----------

    @Test
    void unNombreRepetidoSinDistinguirMayusculasDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());

        crear(ana, ana.presupuestoId, " BANCO ", "AHORRO", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_YA_EXISTE"));

        listar(ana, ana.presupuestoId, false).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void elMismoNombreEsValidoEnPresupuestosDistintos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());

        crear(ana, otro, "Banco", "CORRIENTE", null, null).andExpect(status().isCreated());
    }

    // ---------- listado ----------

    @Test
    void laListaPorDefectoOmiteLasCerradasYIncluirCerradasLasTrae() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());
        long vieja = idDe(crear(ana, ana.presupuestoId, "Antigua", "AHORRO", null, null));
        accion(ana, ana.presupuestoId, vieja, "cerrar").andExpect(status().isOk());
        crear(ana, ana.presupuestoId, "Zeta", "EFECTIVO", null, null)
                .andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("banco"))
                .andExpect(jsonPath("$[1].nombre").value("Zeta"));
        listar(ana, ana.presupuestoId, true)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nombre").value("Antigua"))
                .andExpect(jsonPath("$[1].nombre").value("banco"))
                .andExpect(jsonPath("$[2].nombre").value("Zeta"));
    }

    @Test
    void laListaSoloTieneLasCuentasDelPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());
        crear(ana, otro, "Maleta", "EFECTIVO", null, null).andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, false)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Banco"));
    }

    @Test
    void elDetalleDeUnaCuentaCerradaDevuelve200() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));
        accion(ana, ana.presupuestoId, id, "cerrar").andExpect(status().isOk());

        obtener(ana, ana.presupuestoId, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Banco"))
                .andExpect(jsonPath("$.cerrada").value(true));
    }

    // ---------- edición ----------

    @Test
    void editarCambiaNombreYTipo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Ahorros", "AHORRO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ahorros"))
                .andExpect(jsonPath("$.tipo").value("AHORRO"));
    }

    @Test
    void cambiarSoloLasMayusculasDelPropioNombreEsValido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "banco", "CORRIENTE", null, null));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Banco", "CORRIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Banco"));
    }

    @Test
    void editarAlNombreDeOtraCuentaDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());
        long efectivo = idDe(crear(ana, ana.presupuestoId, "Efectivo", "EFECTIVO", null, null));

        editar(ana, ana.presupuestoId, efectivo, cuerpoEdicion("banco", "EFECTIVO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUENTA_YA_EXISTE"));
    }

    @Test
    void editarConDatosInvalidosDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarDatosInvalidos(
                editar(ana, ana.presupuestoId, id, cuerpoEdicion("  ", "CORRIENTE")), "nombre");
    }

    @Test
    void elSaldoInicialYEnPresupuestoNoSeEditanAunqueElPutLosEnvie() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 1_000L));
        Map<String, Object> cuerpo = cuerpoEdicion("Banco 2", "CORRIENTE");
        cuerpo.put("enPresupuesto", false);
        cuerpo.put("saldoInicial", 9_999L);

        editar(ana, ana.presupuestoId, id, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Banco 2"))
                .andExpect(jsonPath("$.enPresupuesto").value(true))
                .andExpect(jsonPath("$.saldoInicial").value(1_000));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.enPresupuesto").value(true))
                .andExpect(jsonPath("$.saldoInicial").value(1_000));
    }

    @Test
    void cambiarAUnTipoQueNoAdmiteElSaldoNegativoExistenteDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(
                ana, ana.presupuestoId, "Tarjeta", "TARJETA_CREDITO", null, -500L));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Tarjeta", "AHORRO"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.tipo").value("TARJETA_CREDITO"));
    }

    @Test
    void cambiarAUnTipoQueSiAdmiteElSaldoNegativoDevuelve200() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(
                ana, ana.presupuestoId, "Tarjeta", "TARJETA_CREDITO", null, -500L));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Tarjeta", "PRESTAMO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PRESTAMO"));
    }

    // ---------- cerrar y reabrir ----------

    @Test
    void cerrarYReabrirSonIdempotentes() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        accion(ana, ana.presupuestoId, id, "cerrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.cerrada").value(true));
        accion(ana, ana.presupuestoId, id, "cerrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.cerrada").value(true));
        accion(ana, ana.presupuestoId, id, "reabrir")
                .andExpect(status().isOk()).andExpect(jsonPath("$.cerrada").value(false));
        accion(ana, ana.presupuestoId, id, "reabrir")
                .andExpect(status().isOk()).andExpect(jsonPath("$.cerrada").value(false));
    }

    // ---------- aislamiento: presupuesto de otra persona (una aserción por operación) ----------

    @Test
    void crearEnElPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");

        esperarNoEncontrado(crear(beto, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        listar(ana, ana.presupuestoId, true).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listarElPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null)
                .andExpect(status().isCreated());

        esperarNoEncontrado(listar(beto, ana.presupuestoId, false));
    }

    @Test
    void consultarUnaCuentaDelPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarNoEncontrado(obtener(beto, ana.presupuestoId, id));
    }

    @Test
    void editarUnaCuentaDelPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarNoEncontrado(
                editar(beto, ana.presupuestoId, id, cuerpoEdicion("Mio", "AHORRO")));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.nombre").value("Banco"))
                .andExpect(jsonPath("$.tipo").value("CORRIENTE"));
    }

    @Test
    void cerrarUnaCuentaDelPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarNoEncontrado(accion(beto, ana.presupuestoId, id, "cerrar"));

        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.cerrada").value(false));
    }

    @Test
    void reabrirUnaCuentaDelPresupuestoDeOtraPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));
        accion(ana, ana.presupuestoId, id, "cerrar").andExpect(status().isOk());

        esperarNoEncontrado(accion(beto, ana.presupuestoId, id, "reabrir"));

        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.cerrada").value(true));
    }

    @Test
    void laCuentaDeOtraPersonaPorLaUrlDelPropioPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long idDeAna = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarNoEncontrado(obtener(beto, beto.presupuestoId, idDeAna));
    }

    // ---------- aislamiento entre presupuestos de la misma persona ----------

    @Test
    void unaCuentaPorLaUrlDeOtroPresupuestoDeLaMismaPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        esperarNoEncontrado(obtener(ana, otro, id));
        esperarNoEncontrado(editar(ana, otro, id, cuerpoEdicion("Otro", "AHORRO")));
        esperarNoEncontrado(accion(ana, otro, id, "cerrar"));
        esperarNoEncontrado(accion(ana, otro, id, "reabrir"));

        obtener(ana, ana.presupuestoId, id).andExpect(status().isOk());
    }

    @Test
    void unPresupuestoOUnaCuentaInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarNoEncontrado(listar(ana, Long.MAX_VALUE, false));
        esperarNoEncontrado(obtener(ana, ana.presupuestoId, Long.MAX_VALUE));
    }

    // ---------- sin borrado ----------

    @Test
    void noExisteElBorradoYLaCuentaSigueExistiendo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long id = idDe(crear(ana, ana.presupuestoId, "Banco", "CORRIENTE", null, null));

        mockMvc.perform(delete(ruta(ana.presupuestoId) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());

        obtener(ana, ana.presupuestoId, id).andExpect(status().isOk());
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
        return mockMvc.perform(post(RUTA_PRESUPUESTOS)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nombre", nombre))));
    }

    private ResultActions crear(
            Sesion sesion, long presupuestoId, String nombre, String tipo,
            Boolean enPresupuesto, Long saldoInicial) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        if (tipo != null) {
            cuerpo.put("tipo", tipo);
        }
        if (enPresupuesto != null) {
            cuerpo.put("enPresupuesto", enPresupuesto);
        }
        if (saldoInicial != null) {
            cuerpo.put("saldoInicial", saldoInicial);
        }
        return mockMvc.perform(post(ruta(presupuestoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions listar(Sesion sesion, long presupuestoId, boolean incluirCerradas)
            throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId))
                .param("incluirCerradas", String.valueOf(incluirCerradas))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions obtener(Sesion sesion, long presupuestoId, long id) throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions editar(
            Sesion sesion, long presupuestoId, long id, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(put(ruta(presupuestoId) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions accion(Sesion sesion, long presupuestoId, long id, String accion)
            throws Exception {
        return mockMvc.perform(post(ruta(presupuestoId) + "/" + id + "/" + accion)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private static Map<String, Object> cuerpoEdicion(String nombre, String tipo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", tipo);
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
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas";
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
