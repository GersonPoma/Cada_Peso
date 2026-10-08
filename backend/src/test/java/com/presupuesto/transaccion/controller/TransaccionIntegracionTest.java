package com.presupuesto.transaccion.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.presupuesto.comun.config.RelojDePruebaConfig.class)
@Transactional
class TransaccionIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String FECHA = "2026-09-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransaccionRepository transaccionRepository;

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{}";

        esperarNoAutenticado(mockMvc.perform(get(ruta)));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/saldos")));
        esperarNoAutenticado(mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(delete(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/aprobar")));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1/estado")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/mover-cuenta")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/duplicar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/lote")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
    }

    // ---------- creación ----------

    @Test
    void crearMinimaDevuelve201ConLosValoresPorDefecto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);

        crear(ana, ana.presupuestoId, cuerpo(cuenta, -25000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.cuentaId").value(cuenta))
                .andExpect(jsonPath("$.fecha").value(FECHA))
                .andExpect(jsonPath("$.monto").value(-25000))
                .andExpect(jsonPath("$.estado").value("NO_CONCILIADA"))
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.categoriaId", nullValue()))
                .andExpect(jsonPath("$.beneficiario", nullValue()))
                .andExpect(jsonPath("$.memo", nullValue()))
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
    }

    @Test
    void crearConservaElSignoDeUnaEntradaYAceptaFechaFuturaYNoAprobada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, 90000);
        cuerpo.put("fecha", "2099-01-01");
        cuerpo.put("aprobada", false);

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(90000))
                .andExpect(jsonPath("$.fecha").value("2099-01-01"))
                .andExpect(jsonPath("$.aprobada").value(false));
    }

    @Test
    void crearNormalizaBeneficiarioYMemo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -1);
        cuerpo.put("beneficiario", "  Tienda  ");
        cuerpo.put("memo", "   ");

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value("Tienda"))
                .andExpect(jsonPath("$.memo", nullValue()));
    }

    @Test
    void crearConMontoCeroDevuelve400ConElCampoMonto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, cuerpo(cuenta, 0)), "monto");
    }

    @Test
    void crearSinCamposObligatoriosOConLongitudesExcedidasDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);

        Map<String, Object> sinFecha = cuerpo(cuenta, -1);
        sinFecha.remove("fecha");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, sinFecha), "fecha");

        Map<String, Object> sinCuenta = cuerpo(cuenta, -1);
        sinCuenta.remove("cuentaId");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, sinCuenta), "cuentaId");

        Map<String, Object> sinMonto = cuerpo(cuenta, -1);
        sinMonto.remove("monto");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, sinMonto), "monto");

        Map<String, Object> beneficiarioLargo = cuerpo(cuenta, -1);
        beneficiarioLargo.put("beneficiario", "a".repeat(101));
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, beneficiarioLargo), "beneficiario");

        Map<String, Object> memoLargo = cuerpo(cuenta, -1);
        memoLargo.put("memo", "a".repeat(501));
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, memoLargo), "memo");
    }

    @Test
    void crearConFechaImposibleOCuerpoVacioDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> imposible = cuerpo(cuenta, -1);
        imposible.put("fecha", "2026-02-31");

        crear(ana, ana.presupuestoId, imposible)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mockMvc.perform(post(ruta(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    // ---------- cuenta y categorías del presupuesto ----------

    @Test
    void crearConCuentaDeOtroPresupuestoOInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long cuentaDeViajes = crearCuenta(ana, viajes, "Banco", 0);

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo(cuentaDeViajes, -1)));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo(999999, -1)));
        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void crearConCategoriaDeOtroPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long categoriaDeViajes = crearCategoria(ana, viajes, "Hotel");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -1);
        cuerpo.put("categoriaId", categoriaDeViajes);

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo));
    }

    @Test
    void crearConCategoriaAjenaEnUnaSubtransaccionDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long categoriaDeViajes = crearCategoria(ana, viajes, "Hotel");
        long propia = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -3000);
        cuerpo.put("subtransacciones",
                List.of(sub(propia, -1000), sub(categoriaDeViajes, -2000)));

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo));
    }

    @Test
    void unaCategoriaOcultaSePuedeUsarEnLaTransaccionYEnSusPartes() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long oculta = crearCategoria(ana, ana.presupuestoId, "Vieja");
        accion(ana, ana.presupuestoId, "categorias", oculta, "ocultar").andExpect(status().isOk());
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> simple = cuerpo(cuenta, -1);
        simple.put("categoriaId", oculta);
        Map<String, Object> dividida = cuerpo(cuenta, -3000);
        dividida.put("subtransacciones", List.of(sub(oculta, -1000), sub(oculta, -2000)));

        crear(ana, ana.presupuestoId, simple)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaId").value(oculta));
        crear(ana, ana.presupuestoId, dividida)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtransacciones[0].categoriaId").value(oculta));
    }

    // ---------- cuenta cerrada ----------

    @Test
    void crearEnUnaCuentaCerradaDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        cerrarCuenta(ana, ana.presupuestoId, cuenta);

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(cuenta, -1)));
        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void editarEnUnaCuentaCerradaDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1000);
        cerrarCuenta(ana, ana.presupuestoId, cuenta);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, id, edicion(-5)));
        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.monto").value(-1000));
    }

    // ---------- división ----------

    @Test
    void unaDivisionCorrectaDevuelve201ConSusPartesYSinCategoriaPropia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -30000);
        Map<String, Object> segunda = sub(null, -20000);
        segunda.put("memo", "resto");
        cuerpo.put("subtransacciones", List.of(sub(comida, -10000), segunda));

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaId", nullValue()))
                .andExpect(jsonPath("$.subtransacciones", hasSize(2)))
                .andExpect(jsonPath("$.subtransacciones[0].id", notNullValue()))
                .andExpect(jsonPath("$.subtransacciones[0].categoriaId").value(comida))
                .andExpect(jsonPath("$.subtransacciones[0].monto").value(-10000))
                .andExpect(jsonPath("$.subtransacciones[1].categoriaId", nullValue()))
                .andExpect(jsonPath("$.subtransacciones[1].memo").value("resto"));
    }

    @Test
    void unaDivisionConSumaIncorrectaDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -30000);
        cuerpo.put("subtransacciones", List.of(sub(null, -10000), sub(null, -15000)));

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo));
        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void unaSolaSubtransaccionOVeintiunaDevuelve400YVeinteSePermite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);

        Map<String, Object> una = cuerpo(cuenta, -1000);
        una.put("subtransacciones", List.of(sub(null, -1000)));
        esperarDatosInvalidosSinCampos(crear(ana, ana.presupuestoId, una));

        Map<String, Object> veintiuna = cuerpo(cuenta, -21);
        veintiuna.put("subtransacciones", partesIguales(21, -1));
        esperarDatosInvalidosSinCampos(crear(ana, ana.presupuestoId, veintiuna));

        Map<String, Object> veinte = cuerpo(cuenta, -20);
        veinte.put("subtransacciones", partesIguales(20, -1));
        crear(ana, ana.presupuestoId, veinte)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtransacciones", hasSize(20)));
    }

    @Test
    void categoriaPropiaJuntoASubtransaccionesDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2000);
        cuerpo.put("categoriaId", comida);
        cuerpo.put("subtransacciones", List.of(sub(null, -1000), sub(null, -1000)));

        esperarDatosInvalidosSinCampos(crear(ana, ana.presupuestoId, cuerpo));
    }

    @Test
    void unaSubtransaccionConMontoCeroDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -1000);
        cuerpo.put("subtransacciones", List.of(sub(null, -1000), sub(null, 0)));

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, cuerpo), "subtransacciones[1].monto");
    }

    @Test
    void unaListaVaciaDeSubtransaccionesEsUnaTransaccionSimple() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -1000);
        cuerpo.put("subtransacciones", List.of());

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)));
    }

    // ---------- detalle ----------

    @Test
    void obtenerDevuelveTodosLosCampos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1500);

        obtener(ana, ana.presupuestoId, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.cuentaId").value(cuenta))
                .andExpect(jsonPath("$.monto").value(-1500))
                .andExpect(jsonPath("$.estado").value("NO_CONCILIADA"))
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)));
    }

    // ---------- listado y paginación ----------

    @Test
    void listarSinParametrosUsaPaginaCeroYVeinteElementos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        for (int i = 0; i < 25; i++) {
            crearTx(ana, ana.presupuestoId, cuenta, -(i + 1));
        }

        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(20)))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void listarLaUltimaPaginaYUnaPaginaMasAllaDelFinal() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        for (int i = 0; i < 25; i++) {
            crearTx(ana, ana.presupuestoId, cuenta, -(i + 1));
        }

        listar(ana, ana.presupuestoId, Map.of("page", "1", "size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(5)))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        listar(ana, ana.presupuestoId, Map.of("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(25));
    }

    @Test
    void listarSinTransaccionesDevuelveUnaPaginaVacia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void listarConTamanoOPaginaFueraDeRangoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidosSinCampos(listar(ana, ana.presupuestoId, Map.of("size", "0")));
        esperarDatosInvalidosSinCampos(listar(ana, ana.presupuestoId, Map.of("size", "101")));
        esperarDatosInvalidosSinCampos(listar(ana, ana.presupuestoId, Map.of("page", "-1")));
        esperarDatosInvalidosSinCampos(listar(ana, ana.presupuestoId, Map.of("page", "abc")));
    }

    @Test
    void listarAceptaElTamanoMaximo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        listar(ana, ana.presupuestoId, Map.of("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(100));
    }

    @Test
    void listarOrdenaPorFechaDescendenteYLuegoPorIdDescendente() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long viejaA = crearTxFecha(ana, ana.presupuestoId, cuenta, -1, "2026-08-01");
        long mismaA = crearTxFecha(ana, ana.presupuestoId, cuenta, -2, "2026-09-01");
        long mismaB = crearTxFecha(ana, ana.presupuestoId, cuenta, -3, "2026-09-01");
        long nueva = crearTxFecha(ana, ana.presupuestoId, cuenta, -4, "2026-10-01");

        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(jsonPath("$.contenido[*].id")
                        .value(contains((int) nueva, (int) mismaB, (int) mismaA, (int) viejaA)));
    }

    @Test
    void listarFiltraPorCuenta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long efectivo = crearCuenta(ana, ana.presupuestoId, "Efectivo", 0);
        long enBanco = crearTx(ana, ana.presupuestoId, banco, -1);
        crearTx(ana, ana.presupuestoId, efectivo, -2);

        listar(ana, ana.presupuestoId, Map.of("cuentaId", String.valueOf(banco)))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(enBanco));
    }

    @Test
    void listarPorCategoriaCoincideTambienConLasSubtransacciones() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> simple = cuerpo(cuenta, -1);
        simple.put("categoriaId", comida);
        long idSimple = idDe(crear(ana, ana.presupuestoId, simple));
        Map<String, Object> dividida = cuerpo(cuenta, -3000);
        dividida.put("subtransacciones", List.of(sub(comida, -1000), sub(ocio, -2000)));
        long idDividida = idDe(crear(ana, ana.presupuestoId, dividida));
        Map<String, Object> deOcio = cuerpo(cuenta, -2);
        deOcio.put("categoriaId", ocio);
        crear(ana, ana.presupuestoId, deOcio).andExpect(status().isCreated());

        listar(ana, ana.presupuestoId, Map.of("categoriaId", String.valueOf(comida)))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].id")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                (int) idSimple, (int) idDividida)));
    }

    @Test
    void listarFiltraPorRangoDeFechasInclusivoYPorUnSoloExtremo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        crearTxFecha(ana, ana.presupuestoId, cuenta, -1, "2026-08-01");
        long medio = crearTxFecha(ana, ana.presupuestoId, cuenta, -2, "2026-09-01");
        long fin = crearTxFecha(ana, ana.presupuestoId, cuenta, -3, "2026-10-01");

        listar(ana, ana.presupuestoId, Map.of("desde", "2026-09-01", "hasta", "2026-09-01"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(medio));
        listar(ana, ana.presupuestoId, Map.of("desde", "2026-09-01"))
                .andExpect(jsonPath("$.contenido[*].id").value(contains((int) fin, (int) medio)));
        listar(ana, ana.presupuestoId, Map.of("hasta", "2026-08-31"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void listarConRangoInvertidoOFechaInvalidaDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        esperarDatosInvalidosSinCampos(listar(ana, ana.presupuestoId,
                Map.of("desde", "2026-10-02", "hasta", "2026-10-01")));
        esperarDatosInvalidosSinCampos(
                listar(ana, ana.presupuestoId, Map.of("desde", "ayer")));
    }

    @Test
    void listarFiltraPorEstadoYRechazaUnEstadoInvalido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        crearTx(ana, ana.presupuestoId, cuenta, -1);
        long conciliada = crearTx(ana, ana.presupuestoId, cuenta, -2);
        estado(ana, ana.presupuestoId, conciliada, "CONCILIADA").andExpect(status().isOk());

        listar(ana, ana.presupuestoId, Map.of("estado", "CONCILIADA"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(conciliada));
        esperarDatosInvalidosSinCampos(
                listar(ana, ana.presupuestoId, Map.of("estado", "XYZ")));
    }

    @Test
    void listarSoloSinAprobar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        crearTx(ana, ana.presupuestoId, cuenta, -1);
        Map<String, Object> sinAprobar = cuerpo(cuenta, -2);
        sinAprobar.put("aprobada", false);
        long id = idDe(crear(ana, ana.presupuestoId, sinAprobar));

        listar(ana, ana.presupuestoId, Map.of("soloSinAprobar", "true"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
        listar(ana, ana.presupuestoId, Map.of("soloSinAprobar", "false"))
                .andExpect(jsonPath("$.totalElementos").value(2));
        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void listarBuscaTextoSinDistinguirMayusculasYTrataLosComodinesComoLiterales()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long porBeneficiario = crearTxTexto(ana, cuenta, "Supermercado Norte", null);
        long porMemo = crearTxTexto(ana, cuenta, null, "compra norte");
        long porcentaje = crearTxTexto(ana, cuenta, null, "100% listo");
        long guion = crearTxTexto(ana, cuenta, "a_b", null);
        crearTxTexto(ana, cuenta, "axb", "nada");

        listar(ana, ana.presupuestoId, Map.of("q", "NORTE"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].id")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                (int) porBeneficiario, (int) porMemo)));
        listar(ana, ana.presupuestoId, Map.of("q", "%"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(porcentaje));
        listar(ana, ana.presupuestoId, Map.of("q", "_"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(guion));
    }

    @Test
    void listarCombinaTodosLosFiltros() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long efectivo = crearCuenta(ana, ana.presupuestoId, "Efectivo", 0);
        Map<String, Object> objetivo = cuerpo(banco, -1);
        objetivo.put("categoriaId", comida);
        objetivo.put("beneficiario", "Tienda");
        objetivo.put("aprobada", false);
        long id = idDe(crear(ana, ana.presupuestoId, objetivo));
        estado(ana, ana.presupuestoId, id, "CONCILIADA").andExpect(status().isOk());
        Map<String, Object> otraCuenta = new LinkedHashMap<>(objetivo);
        otraCuenta.put("cuentaId", efectivo);
        crear(ana, ana.presupuestoId, otraCuenta).andExpect(status().isCreated());
        Map<String, Object> otroTexto = new LinkedHashMap<>(objetivo);
        otroTexto.put("beneficiario", "Otro");
        crear(ana, ana.presupuestoId, otroTexto).andExpect(status().isCreated());

        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("cuentaId", String.valueOf(banco));
        filtros.put("categoriaId", String.valueOf(comida));
        filtros.put("desde", FECHA);
        filtros.put("hasta", FECHA);
        filtros.put("estado", "CONCILIADA");
        filtros.put("soloSinAprobar", "true");
        filtros.put("q", "tienda");
        listar(ana, ana.presupuestoId, filtros)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
    }

    @Test
    void listarConCuentaOCategoriaDeFiltroAjenaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long cuentaAjena = crearCuenta(ana, viajes, "Banco", 0);
        long categoriaAjena = crearCategoria(ana, viajes, "Hotel");

        esperarNoEncontrado(listar(ana, ana.presupuestoId,
                Map.of("cuentaId", String.valueOf(cuentaAjena))));
        esperarNoEncontrado(listar(ana, ana.presupuestoId,
                Map.of("categoriaId", String.valueOf(categoriaAjena))));
    }

    @Test
    void listarSoloDevuelveLasTransaccionesDelPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long cuentaCasa = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", 0);
        long deCasa = crearTx(ana, ana.presupuestoId, cuentaCasa, -1);
        crearTx(ana, viajes, cuentaViajes, -2);

        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(deCasa));
    }

    // ---------- editar ----------

    @Test
    void editarCambiaLosCamposPeroNoLaCuentaNiElEstado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1000);
        estado(ana, ana.presupuestoId, id, "CONCILIADA").andExpect(status().isOk());
        Map<String, Object> cuerpo = edicion(-2500);
        cuerpo.put("fecha", "2026-09-10");
        cuerpo.put("beneficiario", " Nuevo ");

        editar(ana, ana.presupuestoId, id, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(-2500))
                .andExpect(jsonPath("$.fecha").value("2026-09-10"))
                .andExpect(jsonPath("$.beneficiario").value("Nuevo"))
                .andExpect(jsonPath("$.cuentaId").value(cuenta))
                .andExpect(jsonPath("$.estado").value("CONCILIADA"));
    }

    @Test
    void editarPuedePasarDeSimpleADivididaYViceversa() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -3000);
        Map<String, Object> aDividida = edicion(-3000);
        aDividida.put("subtransacciones", List.of(sub(comida, -1000), sub(null, -2000)));

        editar(ana, ana.presupuestoId, id, aDividida)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtransacciones", hasSize(2)))
                .andExpect(jsonPath("$.categoriaId", nullValue()));

        Map<String, Object> aSimple = edicion(-3000);
        aSimple.put("categoriaId", comida);
        editar(ana, ana.presupuestoId, id, aSimple)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)))
                .andExpect(jsonPath("$.categoriaId").value(comida));
        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)));
    }

    @Test
    void editarConDatosInvalidosNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -3000);

        esperarDatosInvalidos(editar(ana, ana.presupuestoId, id, edicion(0)), "monto");
        Map<String, Object> sumaMala = edicion(-3000);
        sumaMala.put("subtransacciones", List.of(sub(null, -1000), sub(null, -1000)));
        esperarReglaNegocio(editar(ana, ana.presupuestoId, id, sumaMala));
        Map<String, Object> conCategoria = edicion(-2000);
        conCategoria.put("categoriaId", comida);
        conCategoria.put("subtransacciones", List.of(sub(null, -1000), sub(null, -1000)));
        esperarDatosInvalidosSinCampos(editar(ana, ana.presupuestoId, id, conCategoria));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.monto").value(-3000))
                .andExpect(jsonPath("$.subtransacciones", hasSize(0)));
    }

    // ---------- borrar ----------

    @Test
    void borrarDevuelve204YLaTransaccionDejaDeExistirConSusPartes() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> dividida = cuerpo(cuenta, -3000);
        dividida.put("subtransacciones", List.of(sub(null, -1000), sub(null, -2000)));
        long id = idDe(crear(ana, ana.presupuestoId, dividida));

        borrar(ana, ana.presupuestoId, id).andExpect(status().isNoContent());

        esperarNoEncontrado(obtener(ana, ana.presupuestoId, id));
        esperarNoEncontrado(borrar(ana, ana.presupuestoId, id));
        esperarTotal(ana, ana.presupuestoId, 0);
    }

    // ---------- reconciliada ----------

    @Test
    void unaReconciliadaNoSeEditaNiSeMueveNiSeBorraNiCambiaDeEstado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long efectivo = crearCuenta(ana, ana.presupuestoId, "Efectivo", 0);
        long id = crearTx(ana, ana.presupuestoId, banco, -1000);
        reconciliar(id);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, id, edicion(-5)));
        esperarReglaNegocio(mover(ana, ana.presupuestoId, id, efectivo));
        esperarReglaNegocio(borrar(ana, ana.presupuestoId, id));
        esperarReglaNegocio(estado(ana, ana.presupuestoId, id, "NO_CONCILIADA"));
        esperarReglaNegocio(estado(ana, ana.presupuestoId, id, "CONCILIADA"));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(-1000))
                .andExpect(jsonPath("$.cuentaId").value(banco))
                .andExpect(jsonPath("$.estado").value("RECONCILIADA"));
    }

    // ---------- aprobar ----------

    @Test
    void aprobarEsIdempotente() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -1);
        cuerpo.put("aprobada", false);
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));

        accion(ana, ana.presupuestoId, "transacciones", id, "aprobar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true));
        accion(ana, ana.presupuestoId, "transacciones", id, "aprobar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true));
    }

    // ---------- estado ----------

    @Test
    void elEstadoAlternaEntreNoConciliadaYConciliada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1);

        estado(ana, ana.presupuestoId, id, "CONCILIADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCILIADA"));
        estado(ana, ana.presupuestoId, id, "CONCILIADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCILIADA"));
        estado(ana, ana.presupuestoId, id, "NO_CONCILIADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_CONCILIADA"));
    }

    @Test
    void pasarAReconciliadaPorLaApiDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1);

        esperarReglaNegocio(estado(ana, ana.presupuestoId, id, "RECONCILIADA"));
        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.estado").value("NO_CONCILIADA"));
    }

    @Test
    void unEstadoAusenteODesconocidoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1);

        esperarDatosInvalidos(enviar(put(ruta(ana.presupuestoId) + "/" + id + "/estado"),
                ana, Map.of()), "estado");
        esperarDatosInvalidosSinCampos(estado(ana, ana.presupuestoId, id, "XYZ"));
    }

    // ---------- mover cuenta ----------

    @Test
    void moverCambiaLaCuentaYLosSaldosLoReflejan() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 100000);
        long efectivo = crearCuenta(ana, ana.presupuestoId, "Efectivo", 0);
        long id = crearTx(ana, ana.presupuestoId, banco, -30000);

        mover(ana, ana.presupuestoId, id, efectivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(efectivo));

        saldos(ana, ana.presupuestoId)
                .andExpect(jsonPath("$[?(@.cuentaId==" + banco + ")].saldo").value(100000))
                .andExpect(jsonPath("$[?(@.cuentaId==" + efectivo + ")].saldo").value(-30000));
    }

    @Test
    void moverAUnaCuentaDeOtroPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long ajena = crearCuenta(ana, viajes, "Banco", 0);
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, banco, -1);

        esperarNoEncontrado(mover(ana, ana.presupuestoId, id, ajena));
        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.cuentaId").value(banco));
    }

    @Test
    void moverAUnaCuentaCerradaDevuelve422YDesdeUnaCerradaSePermite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long vieja = crearCuenta(ana, ana.presupuestoId, "Vieja", 0);
        long id = crearTx(ana, ana.presupuestoId, vieja, -1);
        cerrarCuenta(ana, ana.presupuestoId, vieja);

        esperarReglaNegocio(mover(ana, ana.presupuestoId, id, vieja));
        mover(ana, ana.presupuestoId, id, banco)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(banco));
    }

    @Test
    void moverSinCuentaDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, banco, -1);

        esperarDatosInvalidos(enviar(post(ruta(ana.presupuestoId) + "/" + id + "/mover-cuenta"),
                ana, Map.of()), "cuentaId");
    }

    // ---------- beneficiario vinculado ----------

    @Test
    void crearConBeneficiarioNuevoLoCreaYLoVincula() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "  Netflix ");

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value("Netflix"))
                .andExpect(jsonPath("$.beneficiarioId").isNumber());

        beneficiarios(ana, ana.presupuestoId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Netflix"))
                .andExpect(jsonPath("$[0].categoriaPredeterminadaId").value((Object) null));
    }

    @Test
    void crearConUnBeneficiarioExistenteConOtrasMayusculasLoReutiliza() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long existente = idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/beneficiarios"),
                ana, Map.of("nombre", "Netflix")));
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "NETFLIX");

        crear(ana, ana.presupuestoId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value("Netflix"))
                .andExpect(jsonPath("$.beneficiarioId").value(existente));

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void sinBeneficiarioLaTransaccionNoSeVinculaNiCreaBeneficiarios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> conEspacios = cuerpo(cuenta, -1);
        conEspacios.put("beneficiario", "   ");

        crear(ana, ana.presupuestoId, cuerpo(cuenta, -1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value((Object) null))
                .andExpect(jsonPath("$.beneficiarioId").value((Object) null));
        crear(ana, ana.presupuestoId, conEspacios)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiarioId").value((Object) null));

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elDetalleYElListadoExponenElBeneficiarioId() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));
        long beneficiarioId = objectMapper.readTree(
                obtener(ana, ana.presupuestoId, id).andReturn().getResponse()
                        .getContentAsString()).get("beneficiarioId").asLong();

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.beneficiarioId").value(beneficiarioId));
        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(jsonPath("$.contenido[0].beneficiarioId").value(beneficiarioId));
    }

    @Test
    void crearConCategoriaRecuerdaLaCategoriaDelBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        cuerpo.put("categoriaId", ocio);

        crear(ana, ana.presupuestoId, cuerpo).andExpect(status().isCreated());

        beneficiarios(ana, ana.presupuestoId)
                .andExpect(jsonPath("$[0].categoriaPredeterminadaId").value(ocio));
    }

    @Test
    void editarConOtraCategoriaActualizaLaCategoriaDelBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        cuerpo.put("categoriaId", ocio);
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));
        Map<String, Object> cambio = edicion(-2500);
        cambio.put("beneficiario", "Netflix");
        cambio.put("categoriaId", comida);

        editar(ana, ana.presupuestoId, id, cambio).andExpect(status().isOk());

        beneficiarios(ana, ana.presupuestoId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoriaPredeterminadaId").value(comida));
    }

    @Test
    void editarPuedeCambiarElBeneficiarioYQuitarloSinBorrarBeneficiarios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));
        Map<String, Object> aSpotify = edicion(-2500);
        aSpotify.put("beneficiario", "Spotify");

        editar(ana, ana.presupuestoId, id, aSpotify)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiario").value("Spotify"))
                .andExpect(jsonPath("$.beneficiarioId").isNumber());
        editar(ana, ana.presupuestoId, id, edicion(-2500))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiario").value((Object) null))
                .andExpect(jsonPath("$.beneficiarioId").value((Object) null));

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void unaTransaccionDivididaNoCambiaLaCategoriaDelBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> simple = cuerpo(cuenta, -1000);
        simple.put("beneficiario", "Hipermaxi");
        simple.put("categoriaId", ocio);
        crear(ana, ana.presupuestoId, simple).andExpect(status().isCreated());
        Map<String, Object> dividida = cuerpo(cuenta, -3000);
        dividida.put("beneficiario", "Hipermaxi");
        dividida.put("subtransacciones", List.of(sub(comida, -1000), sub(comida, -2000)));

        crear(ana, ana.presupuestoId, dividida)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiarioId").isNumber());

        beneficiarios(ana, ana.presupuestoId)
                .andExpect(jsonPath("$[0].categoriaPredeterminadaId").value(ocio));
    }

    @Test
    void crearConCategoriaAjenaDevuelve404YNoCreaElBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long deBeto = crearCategoria(beto, beto.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        cuerpo.put("categoriaId", deBeto);

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo));

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elMismoBeneficiarioEnOtroPresupuestoSeCreaAparte() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long cuentaCasa = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", 0);
        Map<String, Object> enCasa = cuerpo(cuentaCasa, -1);
        enCasa.put("beneficiario", "Netflix");
        Map<String, Object> enViajes = cuerpo(cuentaViajes, -1);
        enViajes.put("beneficiario", "Netflix");

        crear(ana, ana.presupuestoId, enCasa).andExpect(status().isCreated());
        crear(ana, viajes, enViajes).andExpect(status().isCreated());

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(1)));
        beneficiarios(ana, viajes).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void unaTransaccionAnteriorConTextoYSinVinculoSigueFuncionandoYSeVinculaAlEditarla()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo(cuenta, -2500)));
        transaccionRepository.saveAndFlush(
                transaccionAnteriorConTexto(transaccionRepository.findById(id).orElseThrow()));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.beneficiario").value("Tienda"))
                .andExpect(jsonPath("$.beneficiarioId").value((Object) null));
        accion(ana, ana.presupuestoId, "transacciones", id, "aprobar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiario").value("Tienda"))
                .andExpect(jsonPath("$.beneficiarioId").value((Object) null));
        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(0)));

        Map<String, Object> cambio = edicion(-2500);
        cambio.put("beneficiario", "Tienda");
        editar(ana, ana.presupuestoId, id, cambio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiarioId").isNumber());
        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void duplicarConservaElVinculoSinCrearOtroBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -2500);
        cuerpo.put("beneficiario", "Netflix");
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));
        long beneficiarioId = objectMapper.readTree(
                obtener(ana, ana.presupuestoId, id).andReturn().getResponse()
                        .getContentAsString()).get("beneficiarioId").asLong();

        accion(ana, ana.presupuestoId, "transacciones", id, "duplicar")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value("Netflix"))
                .andExpect(jsonPath("$.beneficiarioId").value(beneficiarioId));

        beneficiarios(ana, ana.presupuestoId).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void moverDeCuentaYAprobarNoCambianElBeneficiario() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long efectivo = crearCuenta(ana, ana.presupuestoId, "Efectivo", 0);
        Map<String, Object> cuerpo = cuerpo(banco, -2500);
        cuerpo.put("beneficiario", "Netflix");
        cuerpo.put("aprobada", false);
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));

        mover(ana, ana.presupuestoId, id, efectivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiario").value("Netflix"))
                .andExpect(jsonPath("$.beneficiarioId").isNumber());
        accion(ana, ana.presupuestoId, "transacciones", id, "aprobar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beneficiario").value("Netflix"))
                .andExpect(jsonPath("$.beneficiarioId").isNumber());
    }

    @Test
    void categorizarEnLoteActualizaLaCategoriaDeLosBeneficiariosVinculados() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> deNetflix = cuerpo(cuenta, -1000);
        deNetflix.put("beneficiario", "Netflix");
        Map<String, Object> deSpotify = cuerpo(cuenta, -2000);
        deSpotify.put("beneficiario", "Spotify");
        long a = idDe(crear(ana, ana.presupuestoId, deNetflix));
        long b = idDe(crear(ana, ana.presupuestoId, deSpotify));
        long c = idDe(crear(ana, ana.presupuestoId, cuerpo(cuenta, -3000)));

        lote(ana, ana.presupuestoId, List.of(a, b, c), "CATEGORIZAR", ocio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(3));

        beneficiarios(ana, ana.presupuestoId)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categoriaPredeterminadaId").value(ocio))
                .andExpect(jsonPath("$[1].categoriaPredeterminadaId").value(ocio));
    }

    // ---------- duplicar ----------

    @Test
    void duplicarCopiaConLaFechaDeHoyYEstadoPorDefecto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> cuerpo = cuerpo(cuenta, -3000);
        cuerpo.put("beneficiario", "Tienda");
        cuerpo.put("aprobada", false);
        cuerpo.put("subtransacciones", List.of(sub(comida, -1000), sub(null, -2000)));
        long id = idDe(crear(ana, ana.presupuestoId, cuerpo));
        estado(ana, ana.presupuestoId, id, "CONCILIADA").andExpect(status().isOk());

        accion(ana, ana.presupuestoId, "transacciones", id, "duplicar")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not((int) id)))
                .andExpect(jsonPath("$.fecha").value("2026-10-02"))
                .andExpect(jsonPath("$.estado").value("NO_CONCILIADA"))
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.monto").value(-3000))
                .andExpect(jsonPath("$.beneficiario").value("Tienda"))
                .andExpect(jsonPath("$.subtransacciones", hasSize(2)))
                .andExpect(jsonPath("$.subtransacciones[0].categoriaId").value(comida));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.fecha").value(FECHA))
                .andExpect(jsonPath("$.estado").value("CONCILIADA"))
                .andExpect(jsonPath("$.aprobada").value(false));
        esperarTotal(ana, ana.presupuestoId, 2);
    }

    @Test
    void duplicarUnaReconciliadaSePermiteYEnCuentaCerradaDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long vieja = crearCuenta(ana, ana.presupuestoId, "Vieja", 0);
        long reconciliada = crearTx(ana, ana.presupuestoId, banco, -1);
        reconciliar(reconciliada);
        long enVieja = crearTx(ana, ana.presupuestoId, vieja, -2);
        cerrarCuenta(ana, ana.presupuestoId, vieja);

        accion(ana, ana.presupuestoId, "transacciones", reconciliada, "duplicar")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("NO_CONCILIADA"));
        esperarReglaNegocio(accion(ana, ana.presupuestoId, "transacciones", enVieja, "duplicar"));
    }

    // ---------- lote ----------

    @Test
    void loteCategorizaApruebaYBorra() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long a = crearTx(ana, ana.presupuestoId, cuenta, -1);
        long b = crearTx(ana, ana.presupuestoId, cuenta, -2);

        lote(ana, ana.presupuestoId, List.of(a, b), "CATEGORIZAR", comida)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(2));
        obtener(ana, ana.presupuestoId, a).andExpect(jsonPath("$.categoriaId").value(comida));
        obtener(ana, ana.presupuestoId, b).andExpect(jsonPath("$.categoriaId").value(comida));

        Map<String, Object> sinAprobar = cuerpo(cuenta, -3);
        sinAprobar.put("aprobada", false);
        long c = idDe(crear(ana, ana.presupuestoId, sinAprobar));
        lote(ana, ana.presupuestoId, List.of(a, c), "APROBAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(2));
        obtener(ana, ana.presupuestoId, c).andExpect(jsonPath("$.aprobada").value(true));

        lote(ana, ana.presupuestoId, List.of(a, b, c), "BORRAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(3));
        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void loteConUnIdAjenoOInexistenteDevuelve404YNoAplicaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long propia = crearTx(ana, ana.presupuestoId, cuenta, -1);
        long cuentaBeto = crearCuenta(beto, beto.presupuestoId, "Banco", 0);
        long deBeto = crearTx(beto, beto.presupuestoId, cuentaBeto, -2);

        esperarNoEncontrado(lote(ana, ana.presupuestoId, List.of(propia, deBeto),
                "CATEGORIZAR", comida));
        esperarNoEncontrado(lote(ana, ana.presupuestoId, List.of(propia, 999999L),
                "APROBAR", null));
        esperarNoEncontrado(lote(ana, ana.presupuestoId, List.of(propia, deBeto),
                "BORRAR", null));

        obtener(ana, ana.presupuestoId, propia)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId", nullValue()));
        obtener(beto, beto.presupuestoId, deBeto).andExpect(status().isOk());
    }

    @Test
    void loteConUnaReconciliadaDevuelve422EnCategorizarYBorrarYNoAplicaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long normal = crearTx(ana, ana.presupuestoId, cuenta, -1);
        long reconciliada = crearTx(ana, ana.presupuestoId, cuenta, -2);
        reconciliar(reconciliada);

        esperarReglaNegocio(lote(ana, ana.presupuestoId, List.of(normal, reconciliada),
                "CATEGORIZAR", comida));
        esperarReglaNegocio(lote(ana, ana.presupuestoId, List.of(normal, reconciliada),
                "BORRAR", null));

        obtener(ana, ana.presupuestoId, normal)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId", nullValue()));
        obtener(ana, ana.presupuestoId, reconciliada).andExpect(status().isOk());
    }

    @Test
    void aprobarEnLoteAceptaLasReconciliadas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        Map<String, Object> sinAprobar = cuerpo(cuenta, -1);
        sinAprobar.put("aprobada", false);
        long normal = idDe(crear(ana, ana.presupuestoId, sinAprobar));
        long reconciliada = idDe(crear(ana, ana.presupuestoId, sinAprobar));
        reconciliar(reconciliada);

        lote(ana, ana.presupuestoId, List.of(normal, reconciliada), "APROBAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(2));

        obtener(ana, ana.presupuestoId, reconciliada)
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.estado").value("RECONCILIADA"));
        obtener(ana, ana.presupuestoId, normal).andExpect(jsonPath("$.aprobada").value(true));
    }

    @Test
    void categorizarEnLoteUnaDivididaDevuelve422YNoAplicaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long simple = crearTx(ana, ana.presupuestoId, cuenta, -1);
        Map<String, Object> dividida = cuerpo(cuenta, -2000);
        dividida.put("subtransacciones", List.of(sub(null, -1000), sub(null, -1000)));
        long idDividida = idDe(crear(ana, ana.presupuestoId, dividida));

        esperarReglaNegocio(lote(ana, ana.presupuestoId, List.of(simple, idDividida),
                "CATEGORIZAR", comida));

        obtener(ana, ana.presupuestoId, simple).andExpect(jsonPath("$.categoriaId", nullValue()));
    }

    @Test
    void categorizarEnLoteSinCategoriaODeOtroPresupuestoFalla() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long ajena = crearCategoria(ana, viajes, "Hotel");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1);

        esperarDatosInvalidosSinCampos(
                lote(ana, ana.presupuestoId, List.of(id), "CATEGORIZAR", null));
        esperarNoEncontrado(lote(ana, ana.presupuestoId, List.of(id), "CATEGORIZAR", ajena));
    }

    @Test
    void loteConIdsRepetidosLosCuentaUnaVez() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1);

        lote(ana, ana.presupuestoId, List.of(id, id, id), "APROBAR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectadas").value(1));
    }

    @Test
    void loteConCantidadDeIdsOOperacionInvalidaDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        List<Long> ciento1 = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            ciento1.add(i);
        }

        esperarDatosInvalidos(lote(ana, ana.presupuestoId, List.of(), "APROBAR", null), "ids");
        esperarDatosInvalidos(lote(ana, ana.presupuestoId, ciento1, "APROBAR", null), "ids");
        esperarDatosInvalidos(
                enviar(post(ruta(ana.presupuestoId) + "/lote"), ana, Map.of("ids", List.of(1))),
                "operacion");
        esperarDatosInvalidosSinCampos(
                lote(ana, ana.presupuestoId, List.of(1L), "DESCONOCIDA", null));
    }

    // ---------- saldos ----------

    @Test
    void saldosSinTransaccionesSonElSaldoInicial() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 100000);

        saldos(ana, ana.presupuestoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cuentaId").value(cuenta))
                .andExpect(jsonPath("$[0].saldo").value(100000))
                .andExpect(jsonPath("$[0].saldoConciliado").value(100000));
    }

    @Test
    void saldosSumanTodasYSoloLasConciliadasYReconciliadas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 100000);
        crearTx(ana, ana.presupuestoId, cuenta, -20000);
        long conciliada = crearTx(ana, ana.presupuestoId, cuenta, -10000);
        estado(ana, ana.presupuestoId, conciliada, "CONCILIADA").andExpect(status().isOk());
        long reconciliada = crearTx(ana, ana.presupuestoId, cuenta, 5000);
        reconciliar(reconciliada);

        saldos(ana, ana.presupuestoId)
                .andExpect(jsonPath("$[0].saldo").value(75000))
                .andExpect(jsonPath("$[0].saldoConciliado").value(95000));
    }

    @Test
    void saldosIncluyenLasCuentasCerradasYNoMezclanPresupuestos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long vieja = crearCuenta(ana, ana.presupuestoId, "Vieja", 1000);
        crearTx(ana, ana.presupuestoId, vieja, -400);
        cerrarCuenta(ana, ana.presupuestoId, vieja);
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", 0);
        crearTx(ana, viajes, cuentaViajes, -9999);

        saldos(ana, ana.presupuestoId)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cuentaId").value(vieja))
                .andExpect(jsonPath("$[0].saldo").value(600));
    }

    @Test
    void saldosSinCuentasDevuelveUnaListaVacia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        saldos(ana, ana.presupuestoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- aislamiento ----------

    @Test
    void presupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1000);
        long p = ana.presupuestoId;

        esperarNoEncontrado(crear(beto, p, cuerpo(cuenta, -1)));
        esperarNoEncontrado(listar(beto, p, Map.of()));
        esperarNoEncontrado(obtener(beto, p, id));
        esperarNoEncontrado(editar(beto, p, id, edicion(-5)));
        esperarNoEncontrado(borrar(beto, p, id));
        esperarNoEncontrado(accion(beto, p, "transacciones", id, "aprobar"));
        esperarNoEncontrado(estado(beto, p, id, "CONCILIADA"));
        esperarNoEncontrado(mover(beto, p, id, cuenta));
        esperarNoEncontrado(accion(beto, p, "transacciones", id, "duplicar"));
        esperarNoEncontrado(lote(beto, p, List.of(id), "APROBAR", null));
        esperarNoEncontrado(saldos(beto, p));

        obtener(ana, p, id).andExpect(jsonPath("$.monto").value(-1000));
        esperarTotal(ana, p, 1);
    }

    @Test
    void transaccionDeOtraPersonaDevuelve404PorLaUrlDelPropioPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1000);
        long cuentaBeto = crearCuenta(beto, beto.presupuestoId, "Banco", 0);
        long p = beto.presupuestoId;

        esperarNoEncontrado(obtener(beto, p, id));
        esperarNoEncontrado(editar(beto, p, id, edicion(-5)));
        esperarNoEncontrado(borrar(beto, p, id));
        esperarNoEncontrado(accion(beto, p, "transacciones", id, "aprobar"));
        esperarNoEncontrado(estado(beto, p, id, "CONCILIADA"));
        esperarNoEncontrado(mover(beto, p, id, cuentaBeto));
        esperarNoEncontrado(accion(beto, p, "transacciones", id, "duplicar"));
        esperarNoEncontrado(lote(beto, p, List.of(id), "BORRAR", null));
        esperarNoEncontrado(crear(beto, p, cuerpo(cuenta, -1)));

        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.monto").value(-1000));
    }

    @Test
    void transaccionDeOtroPresupuestoDeLaMismaPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long id = crearTx(ana, ana.presupuestoId, cuenta, -1000);
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", 0);

        esperarNoEncontrado(obtener(ana, viajes, id));
        esperarNoEncontrado(editar(ana, viajes, id, edicion(-5)));
        esperarNoEncontrado(borrar(ana, viajes, id));
        esperarNoEncontrado(accion(ana, viajes, "transacciones", id, "aprobar"));
        esperarNoEncontrado(estado(ana, viajes, id, "CONCILIADA"));
        esperarNoEncontrado(mover(ana, viajes, id, cuentaViajes));
        esperarNoEncontrado(accion(ana, viajes, "transacciones", id, "duplicar"));
        esperarNoEncontrado(lote(ana, viajes, List.of(id), "APROBAR", null));

        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.monto").value(-1000));
    }

    @Test
    void presupuestoOTransaccionInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);

        esperarNoEncontrado(listar(ana, 999999L, Map.of()));
        esperarNoEncontrado(saldos(ana, 999999L));
        esperarNoEncontrado(obtener(ana, ana.presupuestoId, 999999L));
        esperarNoEncontrado(editar(ana, ana.presupuestoId, 999999L, edicion(-5)));
        esperarNoEncontrado(borrar(ana, ana.presupuestoId, 999999L));
        esperarNoEncontrado(mover(ana, ana.presupuestoId, 999999L, cuenta));
    }

    // ---------- transferencias ----------

    @Test
    void detalleYListadoExponenTransaccionParIdYSaldosSumanAmbasPatas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", 100000);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", 40000);
        long normal = crearTx(ana, ana.presupuestoId, banco, 0 + 1);
        Map<String, Object> transferencia = new LinkedHashMap<>();
        transferencia.put("cuentaOrigenId", banco);
        transferencia.put("cuentaDestinoId", ahorros);
        transferencia.put("fecha", FECHA);
        transferencia.put("monto", 30000);
        String cuerpoTransferencia = enviar(
                post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/transferencias"),
                ana, transferencia)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long salida = objectMapper.readTree(cuerpoTransferencia).get("salida").get("id").asLong();
        long entrada =
                objectMapper.readTree(cuerpoTransferencia).get("entrada").get("id").asLong();

        obtener(ana, ana.presupuestoId, salida)
                .andExpect(jsonPath("$.transaccionParId").value(entrada));
        obtener(ana, ana.presupuestoId, normal)
                .andExpect(jsonPath("$.transaccionParId", nullValue()));
        listar(ana, ana.presupuestoId, Map.of())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[?(@.id==" + normal + ")].transaccionParId")
                        .value(contains(nullValue())));
        String cuerpoSaldos = saldos(ana, ana.presupuestoId)
                .andReturn().getResponse().getContentAsString();
        long saldoBanco = 0;
        long saldoAhorros = 0;
        for (tools.jackson.databind.JsonNode fila : objectMapper.readTree(cuerpoSaldos)) {
            if (fila.get("cuentaId").asLong() == banco) {
                saldoBanco = fila.get("saldo").asLong();
            } else if (fila.get("cuentaId").asLong() == ahorros) {
                saldoAhorros = fila.get("saldo").asLong();
            }
        }
        // 100000 + 1 (normal) - 30000 y 40000 + 30000: la transferencia no cambia la suma.
        org.junit.jupiter.api.Assertions.assertEquals(70001L, saldoBanco);
        org.junit.jupiter.api.Assertions.assertEquals(70000L, saldoAhorros);
        org.junit.jupiter.api.Assertions.assertEquals(140001L, saldoBanco + saldoAhorros);
    }


    // ---------- categoría de pago de tarjeta ----------

    @Test
    void crearConLaCategoriaDePagoDa422YNoGuardaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");
        Map<String, Object> cuerpo = cuerpo(cuenta, -1000);
        cuerpo.put("categoriaId", pago);

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo));

        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void editarConLaCategoriaDePagoDa422YNoCambiaLaTransaccion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");
        Map<String, Object> original = cuerpo(cuenta, -1000);
        original.put("categoriaId", comida);
        long id = idDe(crear(ana, ana.presupuestoId, original));
        Map<String, Object> cambio = edicion(-2000);
        cambio.put("categoriaId", pago);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, id, cambio));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(jsonPath("$.monto").value(-1000))
                .andExpect(jsonPath("$.categoriaId").value(comida));
    }

    @Test
    void dividirConUnaParteEnLaCategoriaDePagoDa422YNoGuardaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");
        Map<String, Object> dividida = cuerpo(cuenta, -3000);
        dividida.put("subtransacciones", List.of(sub(comida, -1000), sub(pago, -2000)));

        esperarReglaNegocio(crear(ana, ana.presupuestoId, dividida));

        esperarTotal(ana, ana.presupuestoId, 0);
    }

    @Test
    void loteCategorizarConLaCategoriaDePagoDa422YNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long a = crearTx(ana, ana.presupuestoId, cuenta, -1);
        long b = crearTx(ana, ana.presupuestoId, cuenta, -2);
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");

        esperarReglaNegocio(lote(ana, ana.presupuestoId, List.of(a, b), "CATEGORIZAR", pago));

        obtener(ana, ana.presupuestoId, a).andExpect(jsonPath("$.categoriaId").value(nullValue()));
        obtener(ana, ana.presupuestoId, b).andExpect(jsonPath("$.categoriaId").value(nullValue()));
    }

    @Test
    void filtrarPorLaCategoriaDePagoDa200ConLaListaVacia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        crearTx(ana, ana.presupuestoId, cuenta, -1);
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");

        listar(ana, ana.presupuestoId, Map.of("categoriaId", String.valueOf(pago)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void laCategoriaDePagoDeOtroPresupuestoDa404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", 0);
        long deBeto = crearCategoriaDePago(beto, beto.presupuestoId, "Visa");
        Map<String, Object> cuerpo = cuerpo(cuenta, -1000);
        cuerpo.put("categoriaId", deBeto);

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo));
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

    /** Crea una tarjeta de crédito y devuelve el id de su categoría de pago. */
    private long crearCategoriaDePago(Sesion sesion, long presupuestoId, String tarjeta)
            throws Exception {
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("nombre", tarjeta);
        cuenta.put("tipo", "TARJETA_CREDITO");
        idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"), sesion, cuenta));
        String arbol = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias")
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (tools.jackson.databind.JsonNode grupo : objectMapper.readTree(arbol)) {
            for (tools.jackson.databind.JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()
                        && categoria.get("nombre").asString().equals("Pago: " + tarjeta)) {
                    return categoria.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No hay categoría de pago de " + tarjeta);
    }

    private ResultActions crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return enviar(post(RUTA_PRESUPUESTOS), sesion, Map.of("nombre", nombre));
    }

    private long crearCuenta(Sesion sesion, long presupuestoId, String nombre, long saldoInicial)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", "CORRIENTE");
        cuerpo.put("saldoInicial", saldoInicial);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"),
                sesion, cuerpo));
    }

    private void cerrarCuenta(Sesion sesion, long presupuestoId, long cuentaId) throws Exception {
        accion(sesion, presupuestoId, "cuentas", cuentaId, "cerrar").andExpect(status().isOk());
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

    private ResultActions enviar(
            MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions crear(Sesion sesion, long presupuestoId, Map<String, Object> cuerpo)
            throws Exception {
        return enviar(post(ruta(presupuestoId)), sesion, cuerpo);
    }

    private long crearTx(Sesion sesion, long presupuestoId, long cuentaId, long monto)
            throws Exception {
        return idDe(crear(sesion, presupuestoId, cuerpo(cuentaId, monto)));
    }

    private long crearTxFecha(
            Sesion sesion, long presupuestoId, long cuentaId, long monto, String fecha)
            throws Exception {
        Map<String, Object> cuerpo = cuerpo(cuentaId, monto);
        cuerpo.put("fecha", fecha);
        return idDe(crear(sesion, presupuestoId, cuerpo));
    }

    private long crearTxTexto(Sesion sesion, long cuentaId, String beneficiario, String memo)
            throws Exception {
        Map<String, Object> cuerpo = cuerpo(cuentaId, -1);
        cuerpo.put("beneficiario", beneficiario);
        cuerpo.put("memo", memo);
        return idDe(crear(sesion, sesion.presupuestoId, cuerpo));
    }

    private ResultActions listar(Sesion sesion, long presupuestoId, Map<String, String> params)
            throws Exception {
        MockHttpServletRequestBuilder peticion = get(ruta(presupuestoId))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token));
        params.forEach(peticion::param);
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

    private ResultActions borrar(Sesion sesion, long presupuestoId, long id) throws Exception {
        return mockMvc.perform(delete(ruta(presupuestoId) + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions estado(Sesion sesion, long presupuestoId, long id, String estado)
            throws Exception {
        return enviar(put(ruta(presupuestoId) + "/" + id + "/estado"), sesion,
                Map.of("estado", estado));
    }

    private ResultActions mover(Sesion sesion, long presupuestoId, long id, long cuentaId)
            throws Exception {
        return enviar(post(ruta(presupuestoId) + "/" + id + "/mover-cuenta"), sesion,
                Map.of("cuentaId", cuentaId));
    }

    private ResultActions lote(
            Sesion sesion, long presupuestoId, List<Long> ids, String operacion, Long categoriaId)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ids", ids);
        cuerpo.put("operacion", operacion);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return enviar(post(ruta(presupuestoId) + "/lote"), sesion, cuerpo);
    }

    private ResultActions beneficiarios(Sesion sesion, long presupuestoId) throws Exception {
        return mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/beneficiarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk());
    }

    /** Una transacción de antes de los beneficiarios: con texto y sin vínculo. */
    private Transaccion transaccionAnteriorConTexto(Transaccion transaccion) {
        return transaccion.getBeneficiarioVinculado() == null
                ? conTexto(transaccion, "Tienda")
                : transaccion;
    }

    private static Transaccion conTexto(Transaccion transaccion, String texto) {
        org.springframework.test.util.ReflectionTestUtils
                .setField(transaccion, "beneficiario", texto);
        return transaccion;
    }

    private ResultActions saldos(Sesion sesion, long presupuestoId) throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId) + "/saldos")
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    /** Las reconciliadas solo las crea la conciliación (otro change): aquí, por repositorio. */
    private void reconciliar(long id) {
        Transaccion transaccion = transaccionRepository.findById(id).orElseThrow();
        transaccion.cambiarEstado(EstadoTransaccion.RECONCILIADA);
        transaccionRepository.saveAndFlush(transaccion);
    }

    private void esperarTotal(Sesion sesion, long presupuestoId, int total) throws Exception {
        listar(sesion, presupuestoId, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(total));
    }

    private static Map<String, Object> cuerpo(long cuentaId, long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", FECHA);
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    private static Map<String, Object> edicion(long monto) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fecha", FECHA);
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    private static Map<String, Object> sub(Long categoriaId, long monto) {
        Map<String, Object> parte = new LinkedHashMap<>();
        if (categoriaId != null) {
            parte.put("categoriaId", categoriaId);
        }
        parte.put("monto", monto);
        return parte;
    }

    private static List<Map<String, Object>> partesIguales(int cantidad, long monto) {
        List<Map<String, Object>> partes = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            partes.add(sub(null, monto));
        }
        return partes;
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transacciones";
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

    private static void esperarReglaNegocio(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));
    }

    private static void esperarDatosInvalidos(ResultActions resultado, String campo)
            throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores['" + campo + "']", notNullValue()));
    }

    private static void esperarDatosInvalidosSinCampos(ResultActions resultado)
            throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }
}
