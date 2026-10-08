package com.presupuesto.conciliacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

/** El reloj de pruebas marca 2026-10-02: todas las fechas de los datos son de septiembre. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.presupuesto.comun.config.RelojDePruebaConfig.class)
@Transactional
class ConciliacionIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String MES = "2026-09";
    private static final String EXTRACTO = "2026-09-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransaccionRepository transaccionRepository;

    // ---------- autenticación, aislamiento y orden de errores ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1, 1);

        esperarNoAutenticado(mockMvc.perform(get(ruta + "/estado")
                .param("saldoExtracto", "0").param("fecha", EXTRACTO)));
        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
        esperarNoAutenticado(mockMvc.perform(get(ruta)));
    }

    @Test
    void presupuestoOCuentaAjenosDevuelven404EnCadaRuta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 100_000);
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", "CORRIENTE", true, 0);

        for (long[] par : new long[][] {
                {ana.presupuestoId, 999_999L},
                {viajes, banco},
                {ana.presupuestoId, cuentaViajes}}) {
            esperarNoEncontrado(estado(ana, par[0], par[1], 0, EXTRACTO));
            esperarNoEncontrado(conciliar(ana, par[0], par[1], cuerpo(0, EXTRACTO, null, null)));
            esperarNoEncontrado(historial(ana, par[0], par[1]));
        }
        esperarNoEncontrado(estado(beto, ana.presupuestoId, banco, 0, EXTRACTO));
        esperarNoEncontrado(
                conciliar(beto, ana.presupuestoId, banco, cuerpo(0, EXTRACTO, null, null)));
        esperarNoEncontrado(historial(beto, ana.presupuestoId, banco));
    }

    @Test
    void unaCuentaInexistenteConCuerpoInvalidoDevuelve404Y400SoloSiLaCuentaExiste()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        esperarNoEncontrado(conciliar(ana, ana.presupuestoId, 999_999L, Map.of()));
        esperarDatosInvalidos(conciliar(ana, ana.presupuestoId, banco, Map.of()));
    }

    // ---------- estado ----------

    @Test
    void elEstadoCalculaLaDiferenciaSoloHastaLaFechaDelExtracto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        crearConciliada(ana, banco, "2026-09-03", -120_000, null);
        crearConciliada(ana, banco, "2026-09-05", -80_000, null);
        crearConciliada(ana, banco, "2026-09-12", -50_000, null);
        long pendiente = crearTx(ana, banco, "2026-09-08", -7_000, null);

        estado(ana, ana.presupuestoId, banco, 300_000, EXTRACTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(banco))
                .andExpect(jsonPath("$.saldoConciliado").value(250_000))
                .andExpect(jsonPath("$.saldoConciliadoAlCorte").value(300_000))
                .andExpect(jsonPath("$.saldoExtracto").value(300_000))
                .andExpect(jsonPath("$.diferencia").value(0))
                .andExpect(jsonPath("$.totalNoConciliadas").value(1))
                .andExpect(jsonPath("$.noConciliadas.length()").value(1))
                .andExpect(jsonPath("$.noConciliadas[0].id").value(pendiente));
        assertThat(saldoConciliadoPublicado(ana, banco)).isEqualTo(250_000);
    }

    @Test
    void laDiferenciaPuedeSerNegativaYElExtractoTambien() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, -50_000);
        crearConciliada(ana, visa, "2026-09-03", -10_000, null);

        estado(ana, ana.presupuestoId, visa, -75_000, EXTRACTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoConciliadoAlCorte").value(-60_000))
                .andExpect(jsonPath("$.diferencia").value(-15_000));
    }

    @Test
    void elEstadoNoGuardaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 100_000);
        long conciliada = crearConciliada(ana, banco, "2026-09-03", -1_000, null);

        estado(ana, ana.presupuestoId, banco, 999_999, EXTRACTO).andExpect(status().isOk());

        esperarSinConciliaciones(ana, banco);
        assertThat(estadoDe(conciliada)).isEqualTo(EstadoTransaccion.CONCILIADA);
        esperarTotalTransacciones(ana, 1);
    }

    @Test
    void elEstadoSinParametrosOConFechaFuturaDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        String ruta = ruta(ana.presupuestoId, banco) + "/estado";

        esperarDatosInvalidos(mockMvc.perform(get(ruta).param("fecha", EXTRACTO)
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarDatosInvalidos(mockMvc.perform(get(ruta).param("saldoExtracto", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))));
        esperarDatosInvalidos(estado(ana, ana.presupuestoId, banco, 0, "2026-10-03"));
        estado(ana, ana.presupuestoId, banco, 0, "2026-10-02").andExpect(status().isOk());
    }

    @Test
    void laListaDePendientesEstaAcotadaA100() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        for (int i = 0; i < 101; i++) {
            crearTx(ana, banco, "2026-09-01", -1, null);
        }

        estado(ana, ana.presupuestoId, banco, 0, EXTRACTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noConciliadas.length()").value(100))
                .andExpect(jsonPath("$.totalNoConciliadas").value(101));
    }

    // ---------- crear ----------

    @Test
    void conDiferenciaCeroReconciliaHastaLaFechaYDejaLasPosteriores() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        long a = crearConciliada(ana, banco, "2026-09-03", -120_000, null);
        long b = crearConciliada(ana, banco, "2026-09-05", -80_000, null);
        long posterior = crearConciliada(ana, banco, "2026-09-12", -50_000, null);
        long pendiente = crearTx(ana, banco, "2026-09-04", -1_000, null);

        conciliar(ana, ana.presupuestoId, banco, cuerpo(300_000, EXTRACTO, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuentaId").value(banco))
                .andExpect(jsonPath("$.fecha").value(EXTRACTO))
                .andExpect(jsonPath("$.saldoExtracto").value(300_000))
                .andExpect(jsonPath("$.ajuste").value(0))
                .andExpect(jsonPath("$.transaccionAjusteId", nullValue()))
                .andExpect(jsonPath("$.cantidadReconciliadas").value(2));

        assertThat(estadoDe(a)).isEqualTo(EstadoTransaccion.RECONCILIADA);
        assertThat(estadoDe(b)).isEqualTo(EstadoTransaccion.RECONCILIADA);
        assertThat(estadoDe(posterior)).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(estadoDe(pendiente)).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        esperarTotalTransacciones(ana, 4);
    }

    @Test
    void conDiferenciaYAjusteCreaLaTransaccionYLaReconcilia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        crearConciliada(ana, banco, "2026-09-03", -120_000, null);
        crearConciliada(ana, banco, "2026-09-05", -80_000, null);

        JsonNode registro = leer(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(310_000, EXTRACTO, true, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(10_000))
                .andExpect(jsonPath("$.cantidadReconciliadas").value(3)));

        long ajusteId = registro.get("transaccionAjusteId").asLong();
        mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                        + "/transacciones/" + ajusteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(10_000))
                .andExpect(jsonPath("$.fecha").value(EXTRACTO))
                .andExpect(jsonPath("$.beneficiario").value("Ajuste de conciliación"))
                .andExpect(jsonPath("$.categoriaId", nullValue()))
                .andExpect(jsonPath("$.estado").value("RECONCILIADA"))
                .andExpect(jsonPath("$.aprobada").value(true));
    }

    @Test
    void despuesDeConciliarConAjusteElEstadoConElMismoExtractoDaDiferenciaCero()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        crearConciliada(ana, banco, "2026-09-03", -120_000, null);
        conciliar(ana, ana.presupuestoId, banco, cuerpo(390_000, EXTRACTO, true, null))
                .andExpect(status().isCreated());

        estado(ana, ana.presupuestoId, banco, 390_000, EXTRACTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diferencia").value(0))
                .andExpect(jsonPath("$.saldoConciliadoAlCorte").value(390_000));
    }

    @Test
    void unaSegundaConciliacionIdenticaNoDuplicaElAjuste() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        Map<String, Object> peticion = cuerpo(510_000, EXTRACTO, true, null);

        conciliar(ana, ana.presupuestoId, banco, peticion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(10_000))
                .andExpect(jsonPath("$.cantidadReconciliadas").value(1));
        conciliar(ana, ana.presupuestoId, banco, peticion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(0))
                .andExpect(jsonPath("$.transaccionAjusteId", nullValue()))
                .andExpect(jsonPath("$.cantidadReconciliadas").value(0));

        esperarTotalTransacciones(ana, 1);
    }

    @Test
    void conDiferenciaSinPedirAjusteDevuelve422SinCambiarNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        long conciliada = crearConciliada(ana, banco, "2026-09-03", -1_000, null);

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(1, EXTRACTO, null, null)));
        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(1, EXTRACTO, false, null)));

        esperarSinCambios(ana, banco, conciliada, 1);
    }

    @Test
    void pedirAjusteSinDiferenciaNoCreaNingunaTransaccion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);

        conciliar(ana, ana.presupuestoId, banco, cuerpo(500_000, EXTRACTO, true, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(0))
                .andExpect(jsonPath("$.transaccionAjusteId", nullValue()));

        esperarTotalTransacciones(ana, 0);
    }

    @Test
    void unCuerpoInvalidoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long p = ana.presupuestoId;

        esperarDatosInvalidos(conciliar(ana, p, banco, Map.of("fecha", EXTRACTO)));
        esperarDatosInvalidos(conciliar(ana, p, banco, Map.of("saldoExtracto", 0)));
        esperarDatosInvalidos(conciliar(ana, p, banco, cuerpo(0, "2026-10-03", null, null)));
        esperarSinConciliaciones(ana, banco);
    }

    @Test
    void laFechaDeMananaUTCDevuelve400YLaDeHoyEsValida() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        esperarDatosInvalidos(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(0, "2026-10-03", null, null)));
        conciliar(ana, ana.presupuestoId, banco, cuerpo(0, "2026-10-02", null, null))
                .andExpect(status().isCreated());
    }

    @Test
    void unaCuentaCerradaDevuelve422PeroSeConsulta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        cerrarCuenta(ana, banco);

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(0, EXTRACTO, null, null)));
        estado(ana, ana.presupuestoId, banco, 0, EXTRACTO).andExpect(status().isOk());
        historial(ana, ana.presupuestoId, banco).andExpect(status().isOk());
        esperarSinConciliaciones(ana, banco);
    }

    @Test
    void unaTarjetaDeCreditoSePuedeConciliarConSaldoNegativo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0);
        long gasto = crearConciliada(ana, visa, "2026-09-03", -40_000, null);

        conciliar(ana, ana.presupuestoId, visa, cuerpo(-40_000, EXTRACTO, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadReconciliadas").value(1));

        assertThat(estadoDe(gasto)).isEqualTo(EstadoTransaccion.RECONCILIADA);
    }

    @Test
    void unaCuentaFueraDelPresupuestoSePuedeConciliarConAjusteNegativoSinCategoria()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long externa = crearCuenta(ana, "Inversion", "CORRIENTE", false, 100_000);

        conciliar(ana, ana.presupuestoId, externa, cuerpo(90_000, EXTRACTO, true, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(-10_000));
    }

    @Test
    void unaCuentaFueraDelPresupuestoNoAdmiteCategoria() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long externa = crearCuenta(ana, "Inversion", "CORRIENTE", false, 100_000);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida de prueba");

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, externa,
                cuerpo(110_000, EXTRACTO, true, comida)));

        esperarSinConciliaciones(ana, externa);
    }

    @Test
    void laConciliacionNoTocaOtrasCuentas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long otra = crearCuenta(ana, "Otra", "CORRIENTE", true, 0);
        long deOtra = crearConciliada(ana, otra, "2026-09-03", -5_000, null);

        conciliar(ana, ana.presupuestoId, banco, cuerpo(0, EXTRACTO, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadReconciliadas").value(0));

        assertThat(estadoDe(deOtra)).isEqualTo(EstadoTransaccion.CONCILIADA);
        esperarSinConciliaciones(ana, otra);
    }

    @Test
    void unaReconciliadaPosteriorNoEntraEnLaDiferenciaNiCambia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        crearConciliada(ana, banco, "2026-09-03", -200_000, null);
        long posterior = crearConciliada(ana, banco, "2026-09-12", -50_000, null);
        reconciliar(posterior);
        Transaccion antes = transaccionRepository.findById(posterior).orElseThrow();
        var actualizadaAntes = antes.getFechaActualizacion();

        estado(ana, ana.presupuestoId, banco, 300_000, EXTRACTO)
                .andExpect(jsonPath("$.saldoConciliadoAlCorte").value(300_000))
                .andExpect(jsonPath("$.diferencia").value(0));
        conciliar(ana, ana.presupuestoId, banco, cuerpo(300_000, EXTRACTO, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadReconciliadas").value(1));

        Transaccion despues = transaccionRepository.findById(posterior).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoTransaccion.RECONCILIADA);
        assertThat(despues.getFechaActualizacion()).isEqualTo(actualizadaAntes);
    }

    // ---------- categoría del ajuste e invariante del dinero ----------

    @Test
    void unAjustePositivoSinCategoriaSubeListoParaAsignarYMantieneElInvariante()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long[] e = escenarioComida(ana);
        long banco = e[0];
        long comida = e[1];
        esperarInvariante(ana, 380_000, 300_000, 80_000);

        conciliar(ana, ana.presupuestoId, banco, cuerpo(390_000, EXTRACTO, true, null))
                .andExpect(status().isCreated());

        esperarInvariante(ana, 390_000, 310_000, 80_000);
        assertThat(disponible(ana, comida)).isEqualTo(80_000);
    }

    @Test
    void unAjusteNegativoSinCategoriaDevuelve422SinCambiarNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = escenarioComida(ana)[0];

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(370_000, EXTRACTO, true, null)));

        esperarSinConciliaciones(ana, banco);
        esperarTotalTransacciones(ana, 1);
        esperarInvariante(ana, 380_000, 300_000, 80_000);
    }

    @Test
    void unAjusteNegativoConCategoriaBajaSuDisponibleYMantieneElInvariante() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long[] e = escenarioComida(ana);
        long banco = e[0];
        long comida = e[1];

        conciliar(ana, ana.presupuestoId, banco, cuerpo(370_000, EXTRACTO, true, comida))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(-10_000));

        esperarInvariante(ana, 370_000, 300_000, 70_000);
        assertThat(disponible(ana, comida)).isEqualTo(70_000);
    }

    @Test
    void unAjustePositivoConCategoriaSubeElDisponibleSinTocarListoParaAsignar()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long[] e = escenarioComida(ana);
        long banco = e[0];
        long comida = e[1];

        conciliar(ana, ana.presupuestoId, banco, cuerpo(390_000, EXTRACTO, true, comida))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajuste").value(10_000));

        esperarInvariante(ana, 390_000, 300_000, 90_000);
        assertThat(disponible(ana, comida)).isEqualTo(90_000);
    }

    @Test
    void unAjustePositivoEnTarjetaSinCategoriaDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visa = crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0);

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, visa,
                cuerpo(10_000, EXTRACTO, true, null)));

        esperarSinConciliaciones(ana, visa);
        esperarTotalTransacciones(ana, 0);
    }

    @Test
    void unaCategoriaDePagoDeTarjetaDevuelve422SinCambiarNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long pago = crearCategoriaDePago(ana, "Visa");
        long conciliada = crearConciliada(ana, banco, "2026-09-03", -1_000, null);

        esperarReglaNegocio(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(5_000, EXTRACTO, true, pago)));

        esperarSinCambios(ana, banco, conciliada, 1);
    }

    @Test
    void unaCategoriaAjenaOInexistenteDevuelve404SinCambiarNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long deViajes = crearCategoria(ana, viajes, "Hotel");
        long conciliada = crearConciliada(ana, banco, "2026-09-03", -1_000, null);

        esperarNoEncontrado(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(5_000, EXTRACTO, true, deViajes)));
        esperarNoEncontrado(conciliar(ana, ana.presupuestoId, banco,
                cuerpo(5_000, EXTRACTO, true, 999_999L)));

        esperarSinCambios(ana, banco, conciliada, 1);
    }

    @Test
    void elBeneficiarioDelAjusteApareceEnLosBeneficiarios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        conciliar(ana, ana.presupuestoId, banco, cuerpo(5_000, EXTRACTO, true, null))
                .andExpect(status().isCreated());

        mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/beneficiarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Ajuste de conciliación"));
    }

    // ---------- historial ----------

    @Test
    void elHistorialVaDeLaMasRecienteALaMasAntiguaYSoloDeLaCuenta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long otra = crearCuenta(ana, "Otra", "CORRIENTE", true, 0);
        conciliar(ana, ana.presupuestoId, banco, cuerpo(0, "2026-09-05", null, null))
                .andExpect(status().isCreated());
        conciliar(ana, ana.presupuestoId, banco, cuerpo(0, "2026-09-20", null, null))
                .andExpect(status().isCreated());
        conciliar(ana, ana.presupuestoId, banco, cuerpo(0, "2026-09-20", null, null))
                .andExpect(status().isCreated());
        conciliar(ana, ana.presupuestoId, otra, cuerpo(0, "2026-09-25", null, null))
                .andExpect(status().isCreated());

        JsonNode lista = leer(historial(ana, ana.presupuestoId, banco)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)));

        assertThat(lista.get(0).get("fecha").asString()).isEqualTo("2026-09-20");
        assertThat(lista.get(1).get("fecha").asString()).isEqualTo("2026-09-20");
        assertThat(lista.get(0).get("id").asLong()).isGreaterThan(lista.get(1).get("id").asLong());
        assertThat(lista.get(2).get("fecha").asString()).isEqualTo("2026-09-05");
    }

    @Test
    void unaCuentaSinConciliacionesTieneHistorialVacio() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        historial(ana, ana.presupuestoId, banco)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void putYDeleteSobreLaConciliacionDevuelven405() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        String ruta = ruta(ana.presupuestoId, banco);

        mockMvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(ruta).header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void putYDeleteSobreUnaConciliacionIndividualDevuelven404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long id = idDe(conciliar(ana, ana.presupuestoId, banco, cuerpo(0, EXTRACTO, null, null)));
        String ruta = ruta(ana.presupuestoId, banco) + "/" + id;

        mockMvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ruta).header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isNotFound());
        historial(ana, ana.presupuestoId, banco).andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- escenarios y utilidades ----------

    private record Sesion(String token, long presupuestoId) {
    }

    /**
     * Devuelve la cuenta y la categoría. Cuenta del presupuesto con saldo inicial 500.000, 200.000 asignados a Comida en septiembre
     * y un gasto conciliado de 120.000 en Comida: dinero 380.000, listo 300.000, disponible
     * 80.000.
     */
    private long[] escenarioComida(Sesion ana) throws Exception {
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 500_000);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida de prueba");
        enviar(put(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/meses/" + MES
                + "/categorias/" + comida), ana, Map.of("asignado", 200_000))
                .andExpect(status().isOk());
        crearConciliada(ana, banco, "2026-09-03", -120_000, comida);
        return new long[] {banco, comida};
    }

    private void esperarInvariante(Sesion ana, long dinero, long listo, long disponibles)
            throws Exception {
        JsonNode mes = leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                        + "/meses/" + MES)
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk()));
        long dineroEnCuentas = 0;
        JsonNode saldos = leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                        + "/transacciones/saldos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk()));
        for (JsonNode saldo : saldos) {
            dineroEnCuentas += saldo.get("saldo").asLong();
        }
        assertThat(dineroEnCuentas).isEqualTo(dinero);
        assertThat(mes.get("listoParaAsignar").asLong()).isEqualTo(listo);
        assertThat(mes.get("totalDisponible").asLong()).isEqualTo(disponibles);
        assertThat(dineroEnCuentas)
                .isEqualTo(mes.get("listoParaAsignar").asLong()
                        + mes.get("totalDisponible").asLong());
    }

    private long disponible(Sesion ana, long categoriaId) throws Exception {
        JsonNode mes = leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                        + "/meses/" + MES)
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk()));
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("categoriaId").asLong() == categoriaId) {
                    return categoria.get("disponible").asLong();
                }
            }
        }
        throw new AssertionError("No hay categoría " + categoriaId);
    }

    private long saldoConciliadoPublicado(Sesion ana, long cuentaId) throws Exception {
        JsonNode saldos = leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                        + "/transacciones/saldos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk()));
        for (JsonNode saldo : saldos) {
            if (saldo.get("cuentaId").asLong() == cuentaId) {
                return saldo.get("saldoConciliado").asLong();
            }
        }
        throw new AssertionError("No hay saldo de la cuenta " + cuentaId);
    }

    /** Sin conciliaciones, sin ajuste, y la transacción conciliada intacta. */
    private void esperarSinCambios(Sesion ana, long cuentaId, long conciliada, int total)
            throws Exception {
        esperarSinConciliaciones(ana, cuentaId);
        assertThat(estadoDe(conciliada)).isEqualTo(EstadoTransaccion.CONCILIADA);
        esperarTotalTransacciones(ana, total);
    }

    private void esperarSinConciliaciones(Sesion ana, long cuentaId) throws Exception {
        historial(ana, ana.presupuestoId, cuentaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void esperarTotalTransacciones(Sesion ana, int total) throws Exception {
        mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/transacciones")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(total));
    }

    private EstadoTransaccion estadoDe(long transaccionId) {
        return transaccionRepository.findById(transaccionId).orElseThrow().getEstado();
    }

    /** Las reconciliadas con fecha anterior las crea la conciliación; aquí, por repositorio. */
    private void reconciliar(long id) {
        Transaccion transaccion = transaccionRepository.findById(id).orElseThrow();
        transaccion.cambiarEstado(EstadoTransaccion.RECONCILIADA);
        transaccionRepository.saveAndFlush(transaccion);
    }

    private ResultActions estado(
            Sesion sesion, long presupuestoId, long cuentaId, long saldoExtracto, String fecha)
            throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId, cuentaId) + "/estado")
                .param("saldoExtracto", String.valueOf(saldoExtracto))
                .param("fecha", fecha)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions historial(Sesion sesion, long presupuestoId, long cuentaId)
            throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId, cuentaId))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions conciliar(
            Sesion sesion, long presupuestoId, long cuentaId, Map<String, Object> cuerpo)
            throws Exception {
        return enviar(post(ruta(presupuestoId, cuentaId)), sesion, cuerpo);
    }

    private static Map<String, Object> cuerpo(
            long saldoExtracto, String fecha, Boolean crearAjuste, Long categoriaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("saldoExtracto", saldoExtracto);
        cuerpo.put("fecha", fecha);
        if (crearAjuste != null) {
            cuerpo.put("crearAjuste", crearAjuste);
        }
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return cuerpo;
    }

    private long crearTx(Sesion sesion, long cuentaId, String fecha, long monto, Long categoriaId)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                + "/transacciones"), sesion, cuerpo));
    }

    private long crearConciliada(
            Sesion sesion, long cuentaId, String fecha, long monto, Long categoriaId)
            throws Exception {
        long id = crearTx(sesion, cuentaId, fecha, monto, categoriaId);
        enviar(put(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/transacciones/" + id
                + "/estado"), sesion, Map.of("estado", "CONCILIADA")).andExpect(status().isOk());
        return id;
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

    private long crearCuenta(
            Sesion sesion, String nombre, String tipo, boolean enPresupuesto, long saldoInicial)
            throws Exception {
        return crearCuenta(sesion, sesion.presupuestoId, nombre, tipo, enPresupuesto, saldoInicial);
    }

    private long crearCuenta(
            Sesion sesion, long presupuestoId, String nombre, String tipo,
            boolean enPresupuesto, long saldoInicial) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("tipo", tipo);
        cuerpo.put("enPresupuesto", enPresupuesto);
        cuerpo.put("saldoInicial", saldoInicial);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas"),
                sesion, cuerpo));
    }

    private void cerrarCuenta(Sesion sesion, long cuentaId) throws Exception {
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/cuentas/"
                        + cuentaId + "/cerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk());
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
    private long crearCategoriaDePago(Sesion sesion, String tarjeta) throws Exception {
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("nombre", tarjeta);
        cuenta.put("tipo", "TARJETA_CREDITO");
        idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/cuentas"),
                sesion, cuenta));
        String arbol = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                        + "/categorias")
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

    private JsonNode leer(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId, long cuentaId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas/" + cuentaId + "/conciliacion";
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

    private static void esperarDatosInvalidos(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }
}
