package com.presupuesto.transaccion.controller;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.presupuesto.comun.config.RelojDePruebaConfig.class)
@Transactional
class TransferenciaIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String FECHA = "2026-09-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransaccionRepository transaccionRepository;

    // ---------- autenticación y aislamiento ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{}";

        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(delete(ruta + "/1")));
    }

    @Test
    void presupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        long p = ana.presupuestoId;

        esperarNoEncontrado(crear(beto, p, cuerpo(banco, ahorros, 1, null)));
        esperarNoEncontrado(obtener(beto, p, t.salida));
        esperarNoEncontrado(editar(beto, p, t.salida, edicion(5, null)));
        esperarNoEncontrado(borrar(beto, p, t.salida));
        obtener(ana, p, t.salida).andExpect(status().isOk());
    }

    @Test
    void transferenciaAjenaConLaURLDelPropioPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        long p = beto.presupuestoId;

        esperarNoEncontrado(obtener(beto, p, t.salida));
        esperarNoEncontrado(editar(beto, p, t.entrada, edicion(5, null)));
        esperarNoEncontrado(borrar(beto, p, t.entrada));
        esperarNoEncontrado(crear(beto, p, cuerpo(banco, ahorros, 1, null)));
    }

    @Test
    void transferenciaDeOtroPresupuestoDeLaMismaPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        esperarNoEncontrado(obtener(ana, viajes, t.salida));
        esperarNoEncontrado(editar(ana, viajes, t.salida, edicion(5, null)));
        esperarNoEncontrado(borrar(ana, viajes, t.salida));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo(banco, cuentaViajes, 1, null)));
        esperarNoEncontrado(crear(ana, viajes, cuerpo(banco, ahorros, 1, null)));
    }

    @Test
    void unaTransaccionNormalOInexistenteDevuelve404EnTransferencias() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long normal = idDe(enviar(post(rutaTx(ana.presupuestoId)), ana,
                Map.of("cuentaId", banco, "fecha", FECHA, "monto", -100)));
        long p = ana.presupuestoId;

        esperarNoEncontrado(obtener(ana, p, normal));
        esperarNoEncontrado(editar(ana, p, normal, edicion(5, null)));
        esperarNoEncontrado(borrar(ana, p, normal));
        esperarNoEncontrado(obtener(ana, p, 999999L));
        esperarNoEncontrado(obtener(ana, 999999L, normal));
    }

    // ---------- crear ----------

    @Test
    void crearEntreDosCuentasDelPresupuestoDevuelve201ConLasDosPatas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Map<String, Object> cuerpo = cuerpo(banco, ahorros, 30000, null);
        cuerpo.put("memo", "  mensual  ");

        JsonNode t = leer(crear(ana, ana.presupuestoId, cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.salida.cuentaId").value(banco))
                .andExpect(jsonPath("$.salida.monto").value(-30000))
                .andExpect(jsonPath("$.salida.fecha").value(FECHA))
                .andExpect(jsonPath("$.salida.memo").value("mensual"))
                .andExpect(jsonPath("$.salida.estado").value("NO_CONCILIADA"))
                .andExpect(jsonPath("$.salida.aprobada").value(true))
                .andExpect(jsonPath("$.salida.categoriaId", nullValue()))
                .andExpect(jsonPath("$.entrada.cuentaId").value(ahorros))
                .andExpect(jsonPath("$.entrada.monto").value(30000))
                .andExpect(jsonPath("$.entrada.fecha").value(FECHA))
                .andExpect(jsonPath("$.entrada.memo").value("mensual"))
                .andExpect(jsonPath("$.entrada.categoriaId", nullValue())));

        long salida = t.get("salida").get("id").asLong();
        long entrada = t.get("entrada").get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(
                t.get("salida").get("transaccionParId").asLong()).isEqualTo(entrada);
        org.assertj.core.api.Assertions.assertThat(
                t.get("entrada").get("transaccionParId").asLong()).isEqualTo(salida);
    }

    @Test
    void unaPataDeTransferenciaNoTieneBeneficiarioNiBeneficiarioId() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        for (long pata : List.of(t.salida(), t.entrada())) {
            mockMvc.perform(get(rutaTx(ana.presupuestoId) + "/" + pata)
                            .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.beneficiario", nullValue()))
                    .andExpect(jsonPath("$.beneficiarioId", nullValue()));
        }
        mockMvc.perform(get(rutaTx(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].beneficiario", nullValue()))
                .andExpect(jsonPath("$.contenido[0].beneficiarioId", nullValue()))
                .andExpect(jsonPath("$.contenido[1].beneficiario", nullValue()))
                .andExpect(jsonPath("$.contenido[1].beneficiarioId", nullValue()));
        mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/beneficiarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void crearConMontoCeroNegativoOCamposAusentesDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        long p = ana.presupuestoId;

        esperarDatosInvalidos(crear(ana, p, cuerpo(banco, ahorros, 0, null)), "monto");
        esperarDatosInvalidos(crear(ana, p, cuerpo(banco, ahorros, -5, null)), "monto");
        for (String campo : List.of("cuentaOrigenId", "cuentaDestinoId", "fecha", "monto")) {
            Map<String, Object> sinCampo = cuerpo(banco, ahorros, 1, null);
            sinCampo.remove(campo);
            esperarDatosInvalidos(crear(ana, p, sinCampo), campo);
        }
        Map<String, Object> memoLargo = cuerpo(banco, ahorros, 1, null);
        memoLargo.put("memo", "a".repeat(501));
        esperarDatosInvalidos(crear(ana, p, memoLargo), "memo");
    }

    @Test
    void crearConOrigenIgualAlDestinoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);

        crear(ana, ana.presupuestoId, cuerpo(banco, banco, 100, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        esperarTotalTransacciones(ana, 0);
    }

    @Test
    void crearConUnaCuentaCerradaDevuelve422YNoCreaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        cerrarCuenta(ana, ahorros);

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(banco, ahorros, 100, null)));
        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(ahorros, banco, 100, null)));
        esperarTotalTransacciones(ana, 0);
    }

    // ---------- regla de categoría ----------

    @Test
    void entreCuentasDelPresupuestoLaCategoriaDa422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        long comida = crearCategoria(ana, "Comida");

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(banco, ahorros, 100, comida)));
        esperarTotalTransacciones(ana, 0);
    }

    @Test
    void entreDosCuentasExternasSinCategoriaVaYConCategoriaDa422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long uno = crearCuenta(ana, ana.presupuestoId, "Externa 1", false);
        long dos = crearCuenta(ana, ana.presupuestoId, "Externa 2", false);
        long comida = crearCategoria(ana, "Comida");

        crear(ana, ana.presupuestoId, cuerpo(uno, dos, 100, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salida.categoriaId", nullValue()))
                .andExpect(jsonPath("$.entrada.categoriaId", nullValue()));
        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(uno, dos, 100, comida)));
    }

    @Test
    void delPresupuestoAUnaExternaLaCategoriaEsObligatoriaYVaEnLaSalida() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Inversion", false);
        long comida = crearCategoria(ana, "Comida");

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(banco, externa, 20000, null)));
        crear(ana, ana.presupuestoId, cuerpo(banco, externa, 20000, comida))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salida.categoriaId").value(comida))
                .andExpect(jsonPath("$.entrada.categoriaId", nullValue()));
    }

    @Test
    void deUnaExternaAlPresupuestoLaCategoriaEsOpcionalYVaEnLaEntrada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Inversion", false);
        long comida = crearCategoria(ana, "Comida");

        crear(ana, ana.presupuestoId, cuerpo(externa, banco, 50000, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salida.categoriaId", nullValue()))
                .andExpect(jsonPath("$.entrada.categoriaId", nullValue()));
        crear(ana, ana.presupuestoId, cuerpo(externa, banco, 50000, comida))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salida.categoriaId", nullValue()))
                .andExpect(jsonPath("$.entrada.categoriaId").value(comida));
    }

    @Test
    void categoriaAjenaDa404YOcultaSirve() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Inversion", false);
        long deViajes = crearCategoria(ana, viajes, "Hotel");
        long deBeto = crearCategoria(beto, beto.presupuestoId, "Ajena");
        long oculta = crearCategoria(ana, ana.presupuestoId, "Vieja");
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/categorias/" + oculta
                        + "/ocultar")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk());

        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo(banco, externa, 1, deViajes)));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, cuerpo(banco, externa, 1, deBeto)));
        crear(ana, ana.presupuestoId, cuerpo(banco, externa, 1, oculta))
                .andExpect(status().isCreated());
        Transferencia t = crearOk(ana, banco, externa, 5, oculta);
        esperarNoEncontrado(editar(ana, ana.presupuestoId, t.salida, edicion(5, deViajes)));
        esperarNoEncontrado(editar(ana, ana.presupuestoId, t.salida, edicion(5, deBeto)));
    }

    // ---------- leer ----------

    @Test
    void leerPorCualquieraDeLasDosPatasDevuelveLaMismaTransferencia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        for (long id : List.of(t.salida, t.entrada)) {
            obtener(ana, ana.presupuestoId, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salida.id").value(t.salida))
                    .andExpect(jsonPath("$.entrada.id").value(t.entrada))
                    .andExpect(jsonPath("$.salida.monto").value(-30000))
                    .andExpect(jsonPath("$.entrada.monto").value(30000));
        }
    }

    // ---------- editar ----------

    @Test
    void editarActualizaLasDosPatasYConservaCuentas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        Map<String, Object> cuerpo = edicion(45000, null);
        cuerpo.put("fecha", "2026-09-15");
        cuerpo.put("memo", "ajustada");

        editar(ana, ana.presupuestoId, t.entrada, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salida.monto").value(-45000))
                .andExpect(jsonPath("$.entrada.monto").value(45000))
                .andExpect(jsonPath("$.salida.fecha").value("2026-09-15"))
                .andExpect(jsonPath("$.entrada.fecha").value("2026-09-15"))
                .andExpect(jsonPath("$.salida.memo").value("ajustada"))
                .andExpect(jsonPath("$.entrada.memo").value("ajustada"))
                .andExpect(jsonPath("$.salida.cuentaId").value(banco))
                .andExpect(jsonPath("$.entrada.cuentaId").value(ahorros));
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.entrada.monto").value(45000));
    }

    @Test
    void editarCambiaLaCategoriaSoloEnLaPataDelPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Inversion", false);
        long comida = crearCategoria(ana, "Comida");
        long ocio = crearCategoria(ana, "Ocio");
        Transferencia t = crearOk(ana, banco, externa, 20000, comida);

        editar(ana, ana.presupuestoId, t.salida, edicion(20000, ocio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salida.categoriaId").value(ocio))
                .andExpect(jsonPath("$.entrada.categoriaId", nullValue()));
        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.salida, edicion(20000, null)));
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.categoriaId").value(ocio));
    }

    @Test
    void editarEntreCuentasDelPresupuestoConCategoriaDa422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        long comida = crearCategoria(ana, "Comida");
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.salida, edicion(40000, comida)));
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.monto").value(-30000));
    }

    @Test
    void editarConMontoCeroONegativoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        esperarDatosInvalidos(editar(ana, ana.presupuestoId, t.salida, edicion(0, null)), "monto");
        esperarDatosInvalidos(editar(ana, ana.presupuestoId, t.salida, edicion(-1, null)),
                "monto");
    }

    @Test
    void editarConUnaCuentaCerradaDevuelve422SinCambios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        cerrarCuenta(ana, ahorros);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.salida, edicion(99, null)));
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.monto").value(-30000))
                .andExpect(jsonPath("$.entrada.monto").value(30000));
    }

    @Test
    void editarConUnaPataReconciliadaDevuelve422SinCambiosParciales() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        reconciliar(t.entrada);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.salida, edicion(99, null)));
        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.entrada, edicion(99, null)));
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.monto").value(-30000))
                .andExpect(jsonPath("$.entrada.monto").value(30000));
    }

    // ---------- borrar ----------

    @Test
    void borrarQuitaLasDosPatasConElIdDeCualquiera() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia primera = crearOk(ana, banco, ahorros, 30000, null);
        Transferencia segunda = crearOk(ana, banco, ahorros, 10000, null);

        borrar(ana, ana.presupuestoId, primera.entrada).andExpect(status().isNoContent());
        borrar(ana, ana.presupuestoId, segunda.salida).andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(transaccionRepository.findAllById(
                List.of(primera.salida, primera.entrada, segunda.salida, segunda.entrada)))
                .isEmpty();
        esperarTotalTransacciones(ana, 0);
        esperarNoEncontrado(borrar(ana, ana.presupuestoId, primera.entrada));
        esperarNoEncontrado(borrar(ana, ana.presupuestoId, primera.salida));
        esperarNoEncontrado(borrar(ana, ana.presupuestoId, segunda.salida));
        esperarNoEncontrado(obtener(ana, ana.presupuestoId, segunda.entrada));
    }

    @Test
    void borrarConUnaPataReconciliadaDevuelve422YNoBorraNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        reconciliar(t.salida);

        esperarReglaNegocio(borrar(ana, ana.presupuestoId, t.salida));
        esperarReglaNegocio(borrar(ana, ana.presupuestoId, t.entrada));

        esperarTotalTransacciones(ana, 2);
        obtener(ana, ana.presupuestoId, t.salida).andExpect(status().isOk());
    }

    // ---------- operaciones por pata ----------

    @Test
    void aprobarYCambiarElEstadoAfectanSoloAEsaPata() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        Transaccion entrada = transaccionRepository.findById(t.entrada).orElseThrow();
        Transaccion salida = transaccionRepository.findById(t.salida).orElseThrow();

        mockMvc.perform(put(rutaTx(ana.presupuestoId) + "/" + t.salida + "/estado")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONCILIADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCILIADA"))
                .andExpect(jsonPath("$.transaccionParId").value(t.entrada));
        mockMvc.perform(post(rutaTx(ana.presupuestoId) + "/" + t.entrada + "/aprobar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true));

        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.estado").value("CONCILIADA"))
                .andExpect(jsonPath("$.entrada.estado").value("NO_CONCILIADA"));
        org.assertj.core.api.Assertions.assertThat(entrada.getEstado())
                .isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        org.assertj.core.api.Assertions.assertThat(salida.getEstado())
                .isEqualTo(EstadoTransaccion.CONCILIADA);
    }

    @Test
    void lasRutasDeTransaccionesBloqueanLasPatasConUn422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        long otra = crearCuenta(ana, ana.presupuestoId, "Otra", true);
        long comida = crearCategoria(ana, "Comida");
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);
        long normal = idDe(enviar(post(rutaTx(ana.presupuestoId)), ana,
                Map.of("cuentaId", banco, "fecha", FECHA, "monto", -100)));
        String base = rutaTx(ana.presupuestoId);

        esperarReglaNegocio(enviar(put(base + "/" + t.salida), ana,
                Map.of("fecha", FECHA, "monto", -5)));
        esperarReglaNegocio(mockMvc.perform(delete(base + "/" + t.entrada)
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarReglaNegocio(enviar(post(base + "/" + t.salida + "/mover-cuenta"), ana,
                Map.of("cuentaId", otra)));
        esperarReglaNegocio(mockMvc.perform(post(base + "/" + t.salida + "/duplicar")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarReglaNegocio(lote(ana, List.of(normal, t.salida), "BORRAR", null));
        esperarReglaNegocio(lote(ana, List.of(normal, t.entrada), "CATEGORIZAR", comida));
        lote(ana, List.of(normal, t.entrada), "APROBAR", null).andExpect(status().isOk());

        esperarTotalTransacciones(ana, 3);
        obtener(ana, ana.presupuestoId, t.salida)
                .andExpect(jsonPath("$.salida.monto").value(-30000))
                .andExpect(jsonPath("$.salida.cuentaId").value(banco));
        mockMvc.perform(get(base + "/" + normal)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(jsonPath("$.categoriaId", nullValue()));
    }

    @Test
    void elListadoDevuelveLasPatasComoTransaccionesNormalesConSuPar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long ahorros = crearCuenta(ana, ana.presupuestoId, "Ahorros", true);
        Transferencia t = crearOk(ana, banco, ahorros, 30000, null);

        mockMvc.perform(get(rutaTx(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[?(@.id==" + t.salida + ")].transaccionParId")
                        .value(org.hamcrest.Matchers.contains((int) t.entrada)))
                .andExpect(jsonPath("$.contenido[?(@.id==" + t.entrada + ")].transaccionParId")
                        .value(org.hamcrest.Matchers.contains((int) t.salida)));
    }

    // ---------- ayudas ----------


    // ---------- categoría de pago de tarjeta ----------

    @Test
    void crearConLaCategoriaDePagoDa422YNoGuardaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Externa", false);
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");

        esperarReglaNegocio(crear(ana, ana.presupuestoId, cuerpo(banco, externa, 100, pago)));

        esperarTotalTransacciones(ana, 0);
    }

    @Test
    void editarConLaCategoriaDePagoDa422YNoCambiaLasPatas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        long externa = crearCuenta(ana, ana.presupuestoId, "Externa", false);
        long comida = crearCategoria(ana, "Comida");
        long pago = crearCategoriaDePago(ana, ana.presupuestoId, "Visa");
        Transferencia t = crearOk(ana, banco, externa, 100, comida);

        esperarReglaNegocio(editar(ana, ana.presupuestoId, t.salida(), edicion(250, pago)));

        obtener(ana, ana.presupuestoId, t.salida())
                .andExpect(jsonPath("$.salida.monto").value(-100))
                .andExpect(jsonPath("$.salida.categoriaId").value(comida));
    }

    @Test
    void unPagoDeTarjetaEntreCuentasDelPresupuestoSigueSiendoValidoSinCategoria()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", true);
        Map<String, Object> tarjeta = new LinkedHashMap<>();
        tarjeta.put("nombre", "Visa");
        tarjeta.put("tipo", "TARJETA_CREDITO");
        long visa = idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/cuentas"),
                ana, tarjeta));

        crearOk(ana, banco, visa, 300, null);

        esperarTotalTransacciones(ana, 2);
    }

    private record Sesion(String token, long presupuestoId) {
    }

    private record Transferencia(long salida, long entrada) {
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
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    private long crearCuenta(Sesion sesion, long presupuestoId, String nombre, boolean enPresupuesto)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", "CORRIENTE");
        cuerpo.put("saldoInicial", 0);
        cuerpo.put("enPresupuesto", enPresupuesto);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"),
                sesion, cuerpo));
    }

    private void cerrarCuenta(Sesion sesion, long cuentaId) throws Exception {
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/cuentas/"
                        + cuentaId + "/cerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk());
    }

    private long crearCategoria(Sesion sesion, String nombre) throws Exception {
        return crearCategoria(sesion, sesion.presupuestoId, nombre);
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

    private Transferencia crearOk(
            Sesion sesion, long origen, long destino, long monto, Long categoriaId)
            throws Exception {
        JsonNode t = leer(crear(sesion, sesion.presupuestoId,
                cuerpo(origen, destino, monto, categoriaId)).andExpect(status().isCreated()));
        return new Transferencia(
                t.get("salida").get("id").asLong(), t.get("entrada").get("id").asLong());
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

    private ResultActions lote(Sesion sesion, List<Long> ids, String operacion, Long categoriaId)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ids", ids);
        cuerpo.put("operacion", operacion);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return enviar(post(rutaTx(sesion.presupuestoId) + "/lote"), sesion, cuerpo);
    }

    /** Las reconciliadas solo las crea la conciliación (otro change): aquí, por repositorio. */
    private void reconciliar(long id) {
        Transaccion transaccion = transaccionRepository.findById(id).orElseThrow();
        transaccion.cambiarEstado(EstadoTransaccion.RECONCILIADA);
        transaccionRepository.saveAndFlush(transaccion);
    }

    private void esperarTotalTransacciones(Sesion sesion, int total) throws Exception {
        mockMvc.perform(get(rutaTx(sesion.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(total));
    }

    private static Map<String, Object> cuerpo(
            long origen, long destino, long monto, Long categoriaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaOrigenId", origen);
        cuerpo.put("cuentaDestinoId", destino);
        cuerpo.put("fecha", FECHA);
        cuerpo.put("monto", monto);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return cuerpo;
    }

    private static Map<String, Object> edicion(long monto, Long categoriaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fecha", FECHA);
        cuerpo.put("monto", monto);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return cuerpo;
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return objectMapper.readTree(
                resultado.andReturn().getResponse().getContentAsString());
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transferencias";
    }

    private static String rutaTx(long presupuestoId) {
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
}
