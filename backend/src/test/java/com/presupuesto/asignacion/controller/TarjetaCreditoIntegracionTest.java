package com.presupuesto.asignacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gastar con una tarjeta de crédito reserva el pago en {@code Pago: <tarjeta>}. Cada test crea
 * sus propios datos y solo consulta dentro de su presupuesto; todos comprueban el invariante
 * "efectivo fuera de tarjetas = listoParaAsignar + suma de los disponibles".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TarjetaCreditoIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String ENERO = "2026-01";
    private static final String FEBRERO = "2026-02";
    private static final String FECHA = "2026-01-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- gasto, reembolso y pago ----------

    @Test
    void gastarConLaTarjetaBajaLaCategoriaYSubeLaReservaDePago() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());

        gastar(e, e.visa, -30_000, e.comida);

        assertThat(fila(e, ENERO, e.comida, "asignado")).isEqualTo(100_000);
        assertThat(fila(e, ENERO, e.comida, "actividad")).isEqualTo(-30_000);
        assertThat(fila(e, ENERO, e.comida, "disponible")).isEqualTo(70_000);
        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(30_000);
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(30_000);
        assertThat(listo(e, ENERO)).isEqualTo(50_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void unGastoDivididoReservaElTotalYBajaCadaCategoria() throws Exception {
        Escenario e = escenario(150_000);
        long ocio = crearCategoria(e.ana, e.presupuesto, "Ocio");
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 50_000).andExpect(status().isOk());
        asignar(e.ana, e.presupuesto, ENERO, ocio, 50_000).andExpect(status().isOk());
        Map<String, Object> dividido = cuerpoTx(e.visa, FECHA, -30_000, null);
        dividido.put("subtransacciones", List.of(
                Map.of("categoriaId", e.comida, "monto", -10_000),
                Map.of("categoriaId", ocio, "monto", -20_000)));
        enviar(post(rutaTransacciones(e.presupuesto)), e.ana, dividido)
                .andExpect(status().isCreated());

        assertThat(fila(e, ENERO, e.comida, "actividad")).isEqualTo(-10_000);
        assertThat(fila(e, ENERO, ocio, "actividad")).isEqualTo(-20_000);
        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(30_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void unReembolsoConLaTarjetaDevuelveLaReservaYLaCategoria() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        gastar(e, e.visa, 5_000, e.comida);

        assertThat(fila(e, ENERO, e.comida, "actividad")).isEqualTo(-25_000);
        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(25_000);
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(25_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void pagarLaTarjetaBajaLaReservaYLaDeuda() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        transferir(e.ana, e.presupuesto, e.banco, e.visa, 30_000);

        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(0);
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(0);
        assertThat(fila(e, ENERO, e.comida, "disponible")).isEqualTo(70_000);
        assertThat(listo(e, ENERO)).isEqualTo(50_000);
        assertThat(saldoDe(e, e.visa)).isEqualTo(0);
        esperarInvariante(e, ENERO);
    }

    @Test
    void pagarMasDeLoReservadoSobregastaLaCategoriaDePagoYBajaElListoDelMesSiguiente()
            throws Exception {
        Escenario e = escenario(100_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 30_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        transferir(e.ana, e.presupuesto, e.banco, e.visa, 50_000);

        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(-20_000);
        assertThat(cuerpoFila(e, ENERO, e.pagoVisa).get("sobregastada").asBoolean()).isTrue();
        assertThat(listo(e, ENERO)).isEqualTo(70_000);
        assertThat(fila(e, FEBRERO, e.pagoVisa, "disponible")).isEqualTo(0);
        assertThat(listo(e, FEBRERO)).isEqualTo(50_000);
        esperarInvariante(e, ENERO);
        esperarInvariante(e, FEBRERO);
    }

    // ---------- sobregasto ----------

    @Test
    void elSobregastoConTarjetaSeTrataComoElNormalYReservaElMontoCompleto() throws Exception {
        Escenario e = escenario(100_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 30_000).andExpect(status().isOk());

        gastar(e, e.visa, -40_000, e.comida);

        assertThat(fila(e, ENERO, e.comida, "disponible")).isEqualTo(-10_000);
        assertThat(cuerpoFila(e, ENERO, e.comida).get("sobregastada").asBoolean()).isTrue();
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(40_000);
        assertThat(listo(e, ENERO)).isEqualTo(70_000);
        assertThat(fila(e, FEBRERO, e.comida, "disponible")).isEqualTo(0);
        assertThat(fila(e, FEBRERO, e.pagoVisa, "disponible")).isEqualTo(40_000);
        assertThat(listo(e, FEBRERO)).isEqualTo(60_000);
        esperarInvariante(e, ENERO);
        esperarInvariante(e, FEBRERO);
    }

    @Test
    void laReservaPositivaSeArrastraAlMesSiguiente() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        assertThat(fila(e, FEBRERO, e.pagoVisa, "actividad")).isEqualTo(0);
        assertThat(fila(e, FEBRERO, e.pagoVisa, "disponible")).isEqualTo(30_000);
        esperarInvariante(e, FEBRERO);
    }

    // ---------- deuda previa ----------

    @Test
    void unSaldoInicialNegativoDeLaTarjetaNoReservaNadaYSeFinanciaAsignando() throws Exception {
        Escenario e = escenario(100_000, -80_000);

        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(0);
        assertThat(listo(e, ENERO)).isEqualTo(100_000);

        asignar(e.ana, e.presupuesto, ENERO, e.pagoVisa, 80_000).andExpect(status().isOk());

        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(80_000);
        assertThat(listo(e, ENERO)).isEqualTo(20_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void sePuedeMoverDineroDesdeYHaciaLaCategoriaDePagoRespetandoElDisponible() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        mover(e.ana, e.presupuesto, ENERO, e.pagoVisa, e.comida, 10_000)
                .andExpect(status().isOk());
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(20_000);
        assertThat(fila(e, ENERO, e.comida, "disponible")).isEqualTo(80_000);

        mover(e.ana, e.presupuesto, ENERO, e.comida, e.pagoVisa, 5_000)
                .andExpect(status().isOk());
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(25_000);

        mover(e.ana, e.presupuesto, ENERO, e.pagoVisa, e.comida, 40_000)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));
        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(25_000);
        esperarInvariante(e, ENERO);
    }

    // ---------- tarjeta cerrada y renombrada ----------

    @Test
    void unaTarjetaCerradaOcultaSuCategoriaPeroSuReservaSigueContando() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);
        long listoAntes = listo(e, ENERO);

        accion(e.ana, e.presupuesto, "cuentas", e.visa, "cerrar").andExpect(status().isOk());

        JsonNode sinOcultas = cuerpoMes(e.ana, e.presupuesto, ENERO, false);
        assertThat(buscarCategoria(sinOcultas, e.pagoVisa)).isNull();
        JsonNode conOcultas = cuerpoMes(e.ana, e.presupuesto, ENERO, true);
        assertThat(buscarCategoria(conOcultas, e.pagoVisa).get("disponible").asLong())
                .isEqualTo(30_000);
        assertThat(buscarCategoria(conOcultas, e.pagoVisa).get("oculta").asBoolean()).isTrue();
        assertThat(listo(e, ENERO)).isEqualTo(listoAntes);
        esperarInvariante(e, ENERO);

        accion(e.ana, e.presupuesto, "cuentas", e.visa, "reabrir").andExpect(status().isOk());
        assertThat(buscarCategoria(cuerpoMes(e.ana, e.presupuesto, ENERO, false), e.pagoVisa))
                .isNotNull();
    }

    @Test
    void renombrarLaTarjetaRenombraLaCategoriaYConservaSusCifras() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        gastar(e, e.visa, -30_000, e.comida);

        enviar(put(RUTA_PRESUPUESTOS + "/" + e.presupuesto + "/cuentas/" + e.visa), e.ana,
                Map.of("nombre", "Visa Oro", "tipo", "TARJETA_CREDITO"))
                .andExpect(status().isOk());

        JsonNode pago = cuerpoFila(e, ENERO, e.pagoVisa);
        assertThat(pago.get("nombre").asString()).isEqualTo("Pago: Visa Oro");
        assertThat(pago.get("disponible").asLong()).isEqualTo(30_000);
        assertThat(pago.get("esPagoTarjeta").asBoolean()).isTrue();
        assertThat(pago.get("cuentaId").asLong()).isEqualTo(e.visa);
    }

    // ---------- lo que no reserva ni es ingreso ----------

    @Test
    void unaSalidaSinCategoriaEnLaTarjetaNoReservaNada() throws Exception {
        Escenario e = escenario(150_000);

        gastar(e, e.visa, -8_000, null);

        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(0);
        assertThat(listo(e, ENERO)).isEqualTo(150_000);
    }

    @Test
    void unaEntradaSinCategoriaEnLaTarjetaNoEsIngreso() throws Exception {
        Escenario e = escenario(150_000);

        gastar(e, e.visa, 9_000, null);

        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(0);
        assertThat(listo(e, ENERO)).isEqualTo(150_000);
    }

    @Test
    void unaTransferenciaDesdeUnaCuentaExternaHaciaLaTarjetaNoReservaNiEsIngreso()
            throws Exception {
        Escenario e = escenario(150_000);
        long externa = crearCuenta(e.ana, e.presupuesto, "Externa", "INVERSION", false, 0);

        transferir(e.ana, e.presupuesto, externa, e.visa, 20_000);

        assertThat(fila(e, ENERO, e.pagoVisa, "actividad")).isEqualTo(0);
        assertThat(listo(e, ENERO)).isEqualTo(150_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void lasTarjetasDeSeguimientoNoTienenCategoriaDePago() throws Exception {
        Escenario e = escenario(150_000);

        crearCuenta(e.ana, e.presupuesto, "Visa externa", "TARJETA_CREDITO", false, 0);

        JsonNode mes = cuerpoMes(e.ana, e.presupuesto, ENERO, true);
        int categoriasDePago = 0;
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()) {
                    categoriasDePago++;
                }
            }
        }
        assertThat(categoriasDePago).isEqualTo(1);
    }

    // ---------- aislamiento ----------

    @Test
    void cadaTarjetaReservaSoloEnSuPropiaCategoria() throws Exception {
        Escenario e = escenario(300_000);
        long master = crearCuenta(e.ana, e.presupuesto, "Master", "TARJETA_CREDITO", true, 0);
        long pagoMaster = categoriaDePago(e.ana, e.presupuesto, "Pago: Master");
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 200_000).andExpect(status().isOk());

        gastar(e, e.visa, -30_000, e.comida);
        gastar(e, master, -7_000, e.comida);

        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(30_000);
        assertThat(fila(e, ENERO, pagoMaster, "disponible")).isEqualTo(7_000);
        assertThat(fila(e, ENERO, e.comida, "disponible")).isEqualTo(163_000);
        esperarInvariante(e, ENERO);
    }

    @Test
    void unGastoEnUnPresupuestoNoAfectaLaCategoriaDePagoDeOtro() throws Exception {
        Escenario e = escenario(150_000);
        asignar(e.ana, e.presupuesto, ENERO, e.comida, 100_000).andExpect(status().isOk());
        long otro = idDe(crearPresupuesto(e.ana, "Viajes"));
        long cuentaOtro = crearCuenta(e.ana, otro, "Banco", "CORRIENTE", true, 90_000);
        long visaOtro = crearCuenta(e.ana, otro, "Visa", "TARJETA_CREDITO", true, 0);
        long pagoOtro = categoriaDePago(e.ana, otro, "Pago: Visa");
        long hotel = crearCategoria(e.ana, otro, "Hotel");
        asignar(e.ana, otro, ENERO, hotel, 50_000).andExpect(status().isOk());

        gastar(e, e.visa, -30_000, e.comida);

        assertThat(fila(e, ENERO, e.pagoVisa, "disponible")).isEqualTo(30_000);
        assertThat(cifra(e.ana, otro, ENERO, pagoOtro, "disponible")).isEqualTo(0);
        assertThat(cifra(e.ana, otro, ENERO, hotel, "disponible")).isEqualTo(50_000);
        assertThat(cuentaOtro).isNotEqualTo(visaOtro);
        assertThat(cuerpoMes(e.ana, otro, ENERO, true).get("listoParaAsignar").asLong())
                .isEqualTo(40_000);
    }

    // ---------- ayudas ----------

    private record Sesion(String token, long presupuestoId) {
    }

    /** Una persona con su presupuesto, un banco, la categoría Comida y la tarjeta Visa. */
    private record Escenario(
            Sesion ana, long presupuesto, long banco, long visa, long comida, long pagoVisa) {
    }

    private Escenario escenario(long saldoBanco) throws Exception {
        return escenario(saldoBanco, 0);
    }

    private Escenario escenario(long saldoBanco, long saldoTarjeta) throws Exception {
        Sesion ana = registrar("ana-tarjeta@ejemplo.com");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, saldoBanco);
        long visa = crearCuenta(
                ana, ana.presupuestoId, "Visa", "TARJETA_CREDITO", true, saldoTarjeta);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long pagoVisa = categoriaDePago(ana, ana.presupuestoId, "Pago: Visa");
        return new Escenario(ana, ana.presupuestoId, banco, visa, comida, pagoVisa);
    }

    private void gastar(Escenario e, long cuenta, long monto, Long categoriaId) throws Exception {
        enviar(post(rutaTransacciones(e.presupuesto)), e.ana,
                cuerpoTx(cuenta, FECHA, monto, categoriaId))
                .andExpect(status().isCreated());
    }

    private long fila(Escenario e, String mes, long categoriaId, String campo) throws Exception {
        return cifra(e.ana, e.presupuesto, mes, categoriaId, campo);
    }

    private JsonNode cuerpoFila(Escenario e, String mes, long categoriaId) throws Exception {
        return buscarCategoria(cuerpoMes(e.ana, e.presupuesto, mes, true), categoriaId);
    }

    private long listo(Escenario e, String mes) throws Exception {
        return cuerpoMes(e.ana, e.presupuesto, mes, true).get("listoParaAsignar").asLong();
    }

    /**
     * Efectivo en las cuentas del presupuesto que no son tarjeta = listoParaAsignar + suma de los
     * disponibles de todas las categorías (ocultas y de pago incluidas). Todas las transacciones
     * del test caen en enero, así que el saldo vale para enero y febrero.
     */
    private void esperarInvariante(Escenario e, String mes) throws Exception {
        long efectivo = efectivoFueraDeTarjetas(e);
        JsonNode cuerpo = cuerpoMes(e.ana, e.presupuesto, mes, true);
        long disponibles = 0;
        for (JsonNode grupo : cuerpo.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                disponibles += categoria.get("disponible").asLong();
            }
        }
        assertThat(cuerpo.get("listoParaAsignar").asLong() + disponibles)
                .as("listoParaAsignar + disponibles en %s", mes)
                .isEqualTo(efectivo);
    }

    private long efectivoFueraDeTarjetas(Escenario e) throws Exception {
        String cuentas = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + e.presupuesto + "/cuentas")
                        .param("incluirCerradas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(e.ana.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String saldos = mockMvc.perform(get(rutaTransacciones(e.presupuesto) + "/saldos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(e.ana.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<Long, Long> saldoPorCuenta = new LinkedHashMap<>();
        for (JsonNode saldo : objectMapper.readTree(saldos)) {
            saldoPorCuenta.put(saldo.get("cuentaId").asLong(), saldo.get("saldo").asLong());
        }
        long total = 0;
        for (JsonNode cuenta : objectMapper.readTree(cuentas)) {
            boolean tarjeta = cuenta.get("tipo").asString().equals("TARJETA_CREDITO");
            if (cuenta.get("enPresupuesto").asBoolean() && !tarjeta) {
                total += saldoPorCuenta.get(cuenta.get("id").asLong());
            }
        }
        return total;
    }

    private long saldoDe(Escenario e, long cuentaId) throws Exception {
        String saldos = mockMvc.perform(get(rutaTransacciones(e.presupuesto) + "/saldos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(e.ana.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode saldo : objectMapper.readTree(saldos)) {
            if (saldo.get("cuentaId").asLong() == cuentaId) {
                return saldo.get("saldo").asLong();
            }
        }
        throw new AssertionError("Sin saldo para la cuenta " + cuentaId);
    }

    private long categoriaDePago(Sesion sesion, long presupuestoId, String nombre)
            throws Exception {
        String arbol = mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias")
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode grupo : objectMapper.readTree(arbol)) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("esPagoTarjeta").asBoolean()
                        && categoria.get("nombre").asString().equals(nombre)) {
                    return categoria.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No hay categoría de pago " + nombre);
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

    private ResultActions crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return enviar(post(RUTA_PRESUPUESTOS), sesion, Map.of("nombre", nombre));
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

    private Map<String, Object> cuerpoTx(
            long cuentaId, String fecha, long monto, Long categoriaId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        if (categoriaId != null) {
            cuerpo.put("categoriaId", categoriaId);
        }
        return cuerpo;
    }

    private void transferir(Sesion sesion, long presupuestoId, long origen, long destino,
            long monto) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaOrigenId", origen);
        cuerpo.put("cuentaDestinoId", destino);
        cuerpo.put("fecha", FECHA);
        cuerpo.put("monto", monto);
        enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transferencias"), sesion, cuerpo)
                .andExpect(status().isCreated());
    }

    private ResultActions enviar(
            MockHttpServletRequestBuilder peticion, Sesion sesion, Object cuerpo)
            throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions asignar(
            Sesion sesion, long presupuestoId, String mes, long categoriaId, long asignado)
            throws Exception {
        return enviar(put(ruta(presupuestoId, mes) + "/categorias/" + categoriaId), sesion,
                Map.of("asignado", asignado));
    }

    private ResultActions mover(
            Sesion sesion, long presupuestoId, String mes, long origenId, long destinoId,
            long monto) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("origenId", origenId);
        cuerpo.put("destinoId", destinoId);
        cuerpo.put("monto", monto);
        return enviar(post(ruta(presupuestoId, mes) + "/mover-dinero"), sesion, cuerpo);
    }

    private JsonNode cuerpoMes(Sesion sesion, long presupuestoId, String mes, boolean ocultas)
            throws Exception {
        String cuerpo = mockMvc.perform(get(ruta(presupuestoId, mes))
                        .param("incluirOcultas", String.valueOf(ocultas))
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private long cifra(
            Sesion sesion, long presupuestoId, String mes, long categoriaId, String campo)
            throws Exception {
        return buscarCategoria(cuerpoMes(sesion, presupuestoId, mes, true), categoriaId)
                .get(campo).asLong();
    }

    private static JsonNode buscarCategoria(JsonNode mes, long categoriaId) {
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("categoriaId").asLong() == categoriaId) {
                    return categoria;
                }
            }
        }
        return null;
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId, String mes) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/meses/" + mes;
    }

    private static String rutaTransacciones(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/transacciones";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
