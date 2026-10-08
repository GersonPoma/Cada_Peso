package com.presupuesto.transaccionprogramada.controller;

import static com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.cuerpo;
import static com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.edicion;
import static com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.ruta;
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

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.Sesion;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Flujos HTTP de las transacciones programadas. Todo dentro de una transacción de prueba que se
 * revierte; el generador del arranque está desactivado en los tests (Surefire) y cada prueba solo
 * cuenta dentro de sus propios presupuestos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class TransaccionProgramadaIntegracionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojDePrueba reloj;

    private ClienteProgramadas cliente;

    @BeforeEach
    void preparar() {
        cliente = new ClienteProgramadas(mockMvc, objectMapper);
        // Hoy es 2026-10-10.
        reloj.fijar(Instant.parse("2026-10-10T15:00:00Z"));
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String json = "{}";

        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ruta + "/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(put(ruta + "/1").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ruta + "/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ruta + "/1/pausar")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ruta + "/1/reanudar")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ruta + "/generar")).andExpect(status().isUnauthorized());
    }

    // ---------- crear ----------

    @Test
    void crearMinimaDevuelve201ActivaConLaProximaFechaIgualAlInicioYSinGenerarNada()
            throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long cuenta = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");

        cliente.crear(ana, ana.presupuestoId(), cuerpo(cuenta, "2026-10-15", "MENSUAL", -250000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.cuentaId").value(cuenta))
                .andExpect(jsonPath("$.fechaInicio").value("2026-10-15"))
                .andExpect(jsonPath("$.frecuencia").value("MENSUAL"))
                .andExpect(jsonPath("$.monto").value(-250000))
                .andExpect(jsonPath("$.fechaFin", nullValue()))
                .andExpect(jsonPath("$.categoriaId", nullValue()))
                .andExpect(jsonPath("$.beneficiario", nullValue()))
                .andExpect(jsonPath("$.memo", nullValue()))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-15"))
                .andExpect(jsonPath("$.ultimoError", nullValue()))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
        org.assertj.core.api.Assertions.assertThat(
                cliente.transacciones(ana, ana.presupuestoId()).get("totalElementos").asInt())
                .isZero();
    }

    @Test
    void crearConInicioPasadoNoGeneraNadaDentroDelPost() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long cuenta = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");

        cliente.crear(ana, ana.presupuestoId(), cuerpo(cuenta, "2026-07-05", "MENSUAL", -1000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximaFecha").value("2026-07-05"));

        org.assertj.core.api.Assertions.assertThat(
                cliente.transacciones(ana, ana.presupuestoId()).get("totalElementos").asInt())
                .isZero();
    }

    @Test
    void crearNormalizaBeneficiarioYMemoYAceptaCategoriaYFin() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long cuenta = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");
        long categoria = cliente.crearCategoria(ana, ana.presupuestoId(), "Renta");
        Map<String, Object> cuerpo = cuerpo(cuenta, "2026-10-15", "ANUAL", -1000);
        cuerpo.put("beneficiario", "  Casero  ");
        cuerpo.put("memo", "   ");
        cuerpo.put("categoriaId", categoria);
        cuerpo.put("fechaFin", "2030-10-15");

        cliente.crear(ana, ana.presupuestoId(), cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiario").value("Casero"))
                .andExpect(jsonPath("$.memo", nullValue()))
                .andExpect(jsonPath("$.categoriaId").value(categoria))
                .andExpect(jsonPath("$.fechaFin").value("2030-10-15"));
    }

    @Test
    void crearConDatosInvalidosDevuelve400() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long cuenta = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");
        long p = ana.presupuestoId();

        cliente.crear(ana, p, cuerpo(cuenta, "2026-10-15", "MENSUAL", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores.monto", notNullValue()));
        cliente.crear(ana, p, cuerpo(cuenta, "2026-10-15", "CADA_DOS_DIAS", -1))
                .andExpect(status().isBadRequest());
        Map<String, Object> sinCuenta = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        sinCuenta.remove("cuentaId");
        cliente.crear(ana, p, sinCuenta)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cuentaId", notNullValue()));
        Map<String, Object> largo = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        largo.put("beneficiario", "b".repeat(101));
        largo.put("memo", "m".repeat(501));
        cliente.crear(ana, p, largo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.beneficiario", notNullValue()))
                .andExpect(jsonPath("$.errores.memo", notNullValue()));
        cliente.crear(ana, p, cuerpo(cuenta, "2026-02-31", "MENSUAL", -1))
                .andExpect(status().isBadRequest());
        cliente.sinCuerpo(post(ruta(p)).contentType(MediaType.APPLICATION_JSON).content("{"), ana)
                .andExpect(status().isBadRequest());
    }

    @Test
    void laFechaDeFinNoPuedeSerAnteriorAlInicioPeroPuedeSerIgual() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long cuenta = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");
        Map<String, Object> anterior = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        anterior.put("fechaFin", "2026-10-14");
        Map<String, Object> igual = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        igual.put("fechaFin", "2026-10-15");

        cliente.crear(ana, ana.presupuestoId(), anterior)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        cliente.crear(ana, ana.presupuestoId(), igual).andExpect(status().isCreated());
    }

    @Test
    void cuentaOCategoriaAjenasDan404YLosErroresDeNegocioDan422() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        Sesion beto = cliente.registrar("beto@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long deBeto = cliente.crearCuenta(beto, beto.presupuestoId(), "Banco");
        long categoriaDeBeto = cliente.crearCategoria(beto, beto.presupuestoId(), "Ocio");
        long cerrada = cliente.crearCuenta(ana, p, "Vieja");
        cliente.cerrarCuenta(ana, p, cerrada);
        long pago = cliente.crearCategoriaDePago(ana, p, "Visa");

        cliente.crear(ana, p, cuerpo(deBeto, "2026-10-15", "MENSUAL", -1))
                .andExpect(status().isNotFound());
        cliente.crear(ana, p, cuerpo(999_999, "2026-10-15", "MENSUAL", -1))
                .andExpect(status().isNotFound());
        Map<String, Object> categoriaAjena = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        categoriaAjena.put("categoriaId", categoriaDeBeto);
        cliente.crear(ana, p, categoriaAjena).andExpect(status().isNotFound());
        // Cuenta cerrada y categoría ajena a la vez: gana el 404.
        categoriaAjena.put("cuentaId", cerrada);
        cliente.crear(ana, p, categoriaAjena).andExpect(status().isNotFound());
        cliente.crear(ana, p, cuerpo(cerrada, "2026-10-15", "MENSUAL", -1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));
        Map<String, Object> dePago = cuerpo(cuenta, "2026-10-15", "MENSUAL", -1);
        dePago.put("categoriaId", pago);
        cliente.crear(ana, p, dePago)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"));
    }

    // ---------- consultar ----------

    @Test
    void listarOrdenaPorProximaFechaYSoloActivasExcluyeLasPausadas() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long tarde = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-12-01", "MENSUAL", -1));
        long pronto = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-11-01", "SEMANAL", -1));
        long pausada = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-20", "DIARIA", -1));
        cliente.pausar(ana, p, pausada).andExpect(status().isOk());

        cliente.listar(ana, p)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id").value(contains(
                        (int) pausada, (int) pronto, (int) tarde)));
        cliente.soloActivas(ana, p)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id").value(contains((int) pronto, (int) tarde)));
    }

    @Test
    void obtenerDevuelveLaPlantillaYUnaInexistenteDa404() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-15", "MENSUAL", -7));

        cliente.obtener(ana, p, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.monto").value(-7));
        cliente.obtener(ana, p, 999_999).andExpect(status().isNotFound());
    }

    // ---------- editar ----------

    @Test
    void editarCambiaMontoMemoYBeneficiarioPeroIgnoraLaCuentaYElInicio() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long otraCuenta = cliente.crearCuenta(ana, p, "Efectivo");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-15", "MENSUAL", -7));
        Map<String, Object> cambios = edicion("MENSUAL", -9000);
        cambios.put("memo", "nuevo memo");
        cambios.put("beneficiario", "  Otro  ");
        cambios.put("cuentaId", otraCuenta);
        cambios.put("fechaInicio", "2026-01-01");

        cliente.editar(ana, p, id, cambios)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(-9000))
                .andExpect(jsonPath("$.memo").value("nuevo memo"))
                .andExpect(jsonPath("$.beneficiario").value("Otro"))
                .andExpect(jsonPath("$.cuentaId").value(cuenta))
                .andExpect(jsonPath("$.fechaInicio").value("2026-10-15"))
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-15"));
    }

    @Test
    void cambiarLaFrecuenciaRecalculaLaProximaFechaDesdeLaUltimaGenerada() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        // Mensual el día 15 desde enero; hoy es 2026-10-10: la última generada será el 15-sep.
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-01-15", "MENSUAL", -7));
        cliente.generar(ana, p).andExpect(status().isOk());
        cliente.obtener(ana, p, id).andExpect(jsonPath("$.proximaFecha").value("2026-10-15"));

        cliente.editar(ana, p, id, edicion("SEMANAL", -7))
                .andExpect(status().isOk())
                // 2026-01-15 es jueves: la primera ocurrencia semanal posterior al 15-sep es el 17.
                .andExpect(jsonPath("$.proximaFecha").value("2026-09-17"));
    }

    @Test
    void cambiarLaFrecuenciaDeUnaPausadaNoRecalculaLaProximaFecha() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-06-15", "MENSUAL", -7));
        cliente.pausar(ana, p, id).andExpect(status().isOk());

        cliente.editar(ana, p, id, edicion("SEMANAL", -7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.frecuencia").value("SEMANAL"))
                .andExpect(jsonPath("$.proximaFecha").value("2026-06-15"));
    }

    @Test
    void editarValidaMontoFinCategoriaYPlantilla() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        Sesion beto = cliente.registrar("beto@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-15", "MENSUAL", -7));
        long pago = cliente.crearCategoriaDePago(ana, p, "Visa");
        long ajena = cliente.crearCategoria(beto, beto.presupuestoId(), "Ocio");

        cliente.editar(ana, p, id, edicion("MENSUAL", 0)).andExpect(status().isBadRequest());
        Map<String, Object> finAnterior = edicion("MENSUAL", -7);
        finAnterior.put("fechaFin", "2026-10-14");
        cliente.editar(ana, p, id, finAnterior).andExpect(status().isBadRequest());
        Map<String, Object> dePago = edicion("MENSUAL", -7);
        dePago.put("categoriaId", pago);
        cliente.editar(ana, p, id, dePago).andExpect(status().isUnprocessableEntity());
        Map<String, Object> deOtro = edicion("MENSUAL", -7);
        deOtro.put("categoriaId", ajena);
        cliente.editar(ana, p, id, deOtro).andExpect(status().isNotFound());
        cliente.editar(ana, p, 999_999, edicion("MENSUAL", -7)).andExpect(status().isNotFound());
    }

    // ---------- borrar ----------

    @Test
    void borrarDevuelve204ConservaLasGeneradasSinVinculoYLaPlantillaDeja404() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-08", "DIARIA", -7));
        cliente.generar(ana, p).andExpect(status().isOk())
                .andExpect(jsonPath("$.generadas").value(3));

        cliente.borrar(ana, p, id).andExpect(status().isNoContent());

        cliente.obtener(ana, p, id).andExpect(status().isNotFound());
        JsonNode lista = cliente.transacciones(ana, p);
        org.assertj.core.api.Assertions.assertThat(lista.get("totalElementos").asInt())
                .isEqualTo(3);
        for (JsonNode transaccion : lista.get("contenido")) {
            org.assertj.core.api.Assertions.assertThat(transaccion.get("programadaId").isNull())
                    .isTrue();
        }
        cliente.borrar(ana, p, id).andExpect(status().isNotFound());
    }

    // ---------- pausar y reanudar ----------

    @Test
    void pausarYReanudarSonIdempotentes() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-15", "MENSUAL", -7));

        cliente.reanudar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-15"));
        cliente.pausar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        cliente.pausar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        cliente.reanudar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void reanudarSaltaElPeriodoPausadoYNoGeneraNada() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        // Mensual el día 5 desde junio, pausada; hoy es 2026-10-10.
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-06-05", "MENSUAL", -7));
        cliente.pausar(ana, p, id).andExpect(status().isOk());

        cliente.reanudar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaFecha").value("2026-11-05"));

        org.assertj.core.api.Assertions.assertThat(
                cliente.transacciones(ana, p).get("totalElementos").asInt()).isZero();
    }

    @Test
    void reanudarConOcurrenciaHoyNoLaGeneraPeroElSiguienteGeneradorSi() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        // Mensual el día 10: hoy (2026-10-10) es una ocurrencia.
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-06-10", "MENSUAL", -7));
        cliente.pausar(ana, p, id).andExpect(status().isOk());

        cliente.reanudar(ana, p, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-10"));
        org.assertj.core.api.Assertions.assertThat(
                cliente.transacciones(ana, p).get("totalElementos").asInt()).isZero();

        cliente.generar(ana, p).andExpect(status().isOk())
                .andExpect(jsonPath("$.generadas").value(1));
        cliente.obtener(ana, p, id).andExpect(jsonPath("$.proximaFecha").value("2026-11-10"));
    }

    // ---------- generar ----------

    @Test
    void generarCreaLasVencidasNoConciliadasYSinAprobarConSuProgramadaId() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long categoria = cliente.crearCategoria(ana, p, "Renta");
        Map<String, Object> cuerpo = cuerpo(cuenta, "2026-10-09", "DIARIA", -3000);
        cuerpo.put("categoriaId", categoria);
        cuerpo.put("beneficiario", "Casero");
        cuerpo.put("memo", "alquiler");
        long id = cliente.crearYObtenerId(ana, p, cuerpo);

        cliente.generar(ana, p).andExpect(status().isOk())
                .andExpect(jsonPath("$.generadas").value(2))
                .andExpect(jsonPath("$.plantillasConError").value(0));

        JsonNode lista = cliente.transacciones(ana, p);
        org.assertj.core.api.Assertions.assertThat(lista.get("totalElementos").asInt())
                .isEqualTo(2);
        for (JsonNode t : lista.get("contenido")) {
            org.assertj.core.api.Assertions.assertThat(t.get("programadaId").asLong())
                    .isEqualTo(id);
            org.assertj.core.api.Assertions.assertThat(t.get("estado").asString())
                    .isEqualTo("NO_CONCILIADA");
            org.assertj.core.api.Assertions.assertThat(t.get("aprobada").asBoolean()).isFalse();
            org.assertj.core.api.Assertions.assertThat(t.get("categoriaId").asLong())
                    .isEqualTo(categoria);
            org.assertj.core.api.Assertions.assertThat(t.get("beneficiario").asString())
                    .isEqualTo("Casero");
            org.assertj.core.api.Assertions.assertThat(t.get("beneficiarioId").isNull()).isFalse();
            org.assertj.core.api.Assertions.assertThat(t.get("memo").asString())
                    .isEqualTo("alquiler");
        }
        cliente.obtener(ana, p, id).andExpect(jsonPath("$.proximaFecha").value("2026-10-11"));
        // Repetirlo no crea nada más.
        cliente.generar(ana, p).andExpect(jsonPath("$.generadas").value(0));
    }

    @Test
    void unaTransaccionGeneradaSeConsultaApruebaYBorraSinAfectarLaPlantilla() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        long id = cliente.crearYObtenerId(ana, p, cuerpo(cuenta, "2026-10-10", "MENSUAL", -7));
        cliente.generar(ana, p).andExpect(status().isOk());
        long generada = cliente.transacciones(ana, p).get("contenido").get(0).get("id").asLong();

        cliente.transaccion(ana, p, generada)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programadaId").value(id))
                .andExpect(jsonPath("$.aprobada").value(false));
        mockMvc.perform(post(ClienteProgramadas.RUTA_PRESUPUESTOS + "/" + p + "/transacciones/"
                        + generada + "/aprobar")
                .header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobada").value(true))
                .andExpect(jsonPath("$.programadaId").value(id));
        mockMvc.perform(delete(ClienteProgramadas.RUTA_PRESUPUESTOS + "/" + p + "/transacciones/"
                        + generada)
                .header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isNoContent());

        cliente.obtener(ana, p, id).andExpect(jsonPath("$.proximaFecha").value("2026-11-10"));
        cliente.generar(ana, p).andExpect(jsonPath("$.generadas").value(0));
        org.assertj.core.api.Assertions.assertThat(
                cliente.transacciones(ana, p).get("totalElementos").asInt()).isZero();
    }

    @Test
    void unaTransaccionManualTieneProgramadaIdNulo() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long p = ana.presupuestoId();
        long cuenta = cliente.crearCuenta(ana, p, "Banco");
        Map<String, Object> manual = new LinkedHashMap<>();
        manual.put("cuentaId", cuenta);
        manual.put("fecha", "2026-10-01");
        manual.put("monto", -100);

        ResultActions creada = cliente.enviar(post(ClienteProgramadas.RUTA_PRESUPUESTOS + "/" + p
                + "/transacciones"), ana, manual);
        creada.andExpect(status().isCreated())
                .andExpect(jsonPath("$.programadaId", nullValue()))
                .andExpect(jsonPath("$.aprobada").value(true));
    }
}
