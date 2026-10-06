package com.presupuesto.asignacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsignacionIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String ENERO = "2026-01";
    private static final String FEBRERO = "2026-02";
    private static final String MARZO = "2026-03";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AsignacionMensualRepository asignacionRepository;

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1, ENERO);

        esperarNoAutenticado(mockMvc.perform(get(ruta)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/categorias/1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"asignado\": 1}")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/mover-dinero")
                .contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    // ---------- mes inválido, futuro y pasado ----------

    @Test
    void unMesInvalidoDevuelve400DatosInvalidosEnLasTresRutas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");

        for (String mes : List.of("2026-13", "2026-1", "2026-00", "enero", "1999-12", "2101-01")) {
            esperarDatosInvalidosSinCampos(obtener(ana, ana.presupuestoId, mes, false));
            esperarDatosInvalidosSinCampos(asignar(ana, ana.presupuestoId, mes, comida, 1));
            esperarDatosInvalidosSinCampos(mover(ana, ana.presupuestoId, mes, comida, ocio, 1));
        }
    }

    @Test
    void losMesesFuturosYPasadosSePuedenConsultarYAsignar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");

        asignar(ana, ana.presupuestoId, "2030-05", comida, 10_000).andExpect(status().isOk());
        asignar(ana, ana.presupuestoId, "2020-01", comida, 20_000).andExpect(status().isOk());
        obtener(ana, ana.presupuestoId, "2030-05", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2030-05"));
        obtener(ana, ana.presupuestoId, "2020-01", false).andExpect(status().isOk());
    }

    // ---------- asignar ----------

    @Test
    void asignarCreaLaFilaYDevuelveLaCategoriaCalculadaYElListoActualizado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 500_000);

        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.categoriaId").value(comida))
                .andExpect(jsonPath("$.categoria.nombre").value("Comida"))
                .andExpect(jsonPath("$.categoria.asignado").value(100_000))
                .andExpect(jsonPath("$.categoria.actividad").value(0))
                .andExpect(jsonPath("$.categoria.disponible").value(100_000))
                .andExpect(jsonPath("$.categoria.sobregastada").value(false))
                .andExpect(jsonPath("$.listoParaAsignar").value(400_000));
    }

    @Test
    void asignarDeNuevoActualizaLaMismaFilaSinSumar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long antes = asignacionRepository.count();

        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        asignar(ana, ana.presupuestoId, ENERO, comida, 60_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.asignado").value(60_000));

        assertThat(asignacionRepository.count()).isEqualTo(antes + 1);
    }

    @Test
    void asignarCeroYNegativoSePermite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");

        asignar(ana, ana.presupuestoId, ENERO, comida, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.asignado").value(0));
        asignar(ana, ana.presupuestoId, ENERO, comida, -5_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.asignado").value(-5_000));
    }

    @Test
    void asignarSinAsignadoODeOtroTipoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        String ruta = ruta(ana.presupuestoId, ENERO) + "/categorias/" + comida;

        esperarDatosInvalidos(enviar(put(ruta), ana, Map.of()), "asignado");
        enviar(put(ruta), ana, Map.of("asignado", "mucho"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void unaCategoriaOcultaSePuedeAsignar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long oculta = crearCategoria(ana, ana.presupuestoId, "Vieja");
        accionCategoria(ana, ana.presupuestoId, oculta, "ocultar").andExpect(status().isOk());

        asignar(ana, ana.presupuestoId, ENERO, oculta, 5_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.oculta").value(true))
                .andExpect(jsonPath("$.categoria.asignado").value(5_000));
    }

    @Test
    void cadaMesTieneSuPropiaAsignacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");

        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        asignar(ana, ana.presupuestoId, FEBRERO, comida, 30_000).andExpect(status().isOk());

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "asignado")).isEqualTo(100_000);
        assertThat(cifra(ana, ana.presupuestoId, FEBRERO, comida, "asignado")).isEqualTo(30_000);
    }

    // ---------- consultar ----------

    @Test
    void unPresupuestoRecienCreadoDaTodoEnCero() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        JsonNode mes = cuerpoMes(ana, ana.presupuestoId, ENERO, false);

        assertThat(mes.get("mes").asString()).isEqualTo(ENERO);
        assertThat(mes.get("listoParaAsignar").asLong()).isZero();
        assertThat(mes.get("totalAsignado").asLong()).isZero();
        assertThat(mes.get("totalActividad").asLong()).isZero();
        assertThat(mes.get("totalDisponible").asLong()).isZero();
        assertThat(mes.get("grupos").size()).isPositive();
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                assertThat(categoria.get("asignado").asLong()).isZero();
                assertThat(categoria.get("actividad").asLong()).isZero();
                assertThat(categoria.get("disponible").asLong()).isZero();
                assertThat(categoria.get("sobregastada").asBoolean()).isFalse();
            }
        }
    }

    @Test
    void incluirOcultasTraeLosGruposYCategoriasOcultosYPorDefectoNo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupoOculto = crearGrupo(ana, ana.presupuestoId, "Archivo");
        accion(ana, ana.presupuestoId, "grupos-categorias", grupoOculto, "ocultar")
                .andExpect(status().isOk());
        long oculta = crearCategoria(ana, ana.presupuestoId, "Vieja");
        accionCategoria(ana, ana.presupuestoId, oculta, "ocultar").andExpect(status().isOk());

        JsonNode sin = cuerpoMes(ana, ana.presupuestoId, ENERO, false);
        JsonNode con = cuerpoMes(ana, ana.presupuestoId, ENERO, true);

        assertThat(buscarGrupo(sin, grupoOculto)).isNull();
        assertThat(buscarCategoria(sin, oculta)).isNull();
        assertThat(buscarGrupo(con, grupoOculto)).isNotNull();
        assertThat(buscarCategoria(con, oculta).get("oculta").asBoolean()).isTrue();
    }

    @Test
    void losTotalesDelMesSumanLasCategoriasMostradas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-10", -30_000, comida);
        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        asignar(ana, ana.presupuestoId, ENERO, ocio, 20_000).andExpect(status().isOk());

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.totalAsignado").value(120_000))
                .andExpect(jsonPath("$.totalActividad").value(-30_000))
                .andExpect(jsonPath("$.totalDisponible").value(90_000));
    }

    // ---------- actividad ----------

    @Test
    void laActividadSumaLasTransaccionesDelMesCalendarioPorFecha() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-05", -12_000, comida);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-31", -8_000, comida);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-02-01", -5_000, comida);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-20", 4_000, comida);

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "actividad")).isEqualTo(-16_000);
        assertThat(cifra(ana, ana.presupuestoId, FEBRERO, comida, "actividad")).isEqualTo(-5_000);
    }

    @Test
    void laActividadIncluyeLasSubtransacciones() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        Map<String, Object> cuerpo = cuerpoTx(cuenta, "2026-01-10", -30_000, null);
        cuerpo.put("subtransacciones", List.of(
                Map.of("categoriaId", comida, "monto", -10_000),
                Map.of("categoriaId", ocio, "monto", -20_000)));
        enviar(post(rutaTransacciones(ana.presupuestoId)), ana, cuerpo)
                .andExpect(status().isCreated());

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "actividad")).isEqualTo(-10_000);
        assertThat(cifra(ana, ana.presupuestoId, ENERO, ocio, "actividad")).isEqualTo(-20_000);
        assertThat(cuerpoMes(ana, ana.presupuestoId, ENERO, false)
                .get("listoParaAsignar").asLong()).isZero();
    }

    @Test
    void lasCuentasFueraDelPresupuestoSeIgnoranYLasCerradasCuentan() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        long fuera = crearCuenta(ana, ana.presupuestoId, "Seguimiento", "INVERSION", false, 0);
        long vieja = crearCuenta(ana, ana.presupuestoId, "Vieja", "EFECTIVO", true, 0);
        crearTx(ana, ana.presupuestoId, banco, "2026-01-05", -1_000, comida);
        crearTx(ana, ana.presupuestoId, fuera, "2026-01-05", -50_000, comida);
        crearTx(ana, ana.presupuestoId, vieja, "2026-01-05", -7_000, comida);
        accion(ana, ana.presupuestoId, "cuentas", vieja, "cerrar").andExpect(status().isOk());

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "actividad")).isEqualTo(-8_000);
    }

    @Test
    void lasCategoriasDeOtroPresupuestoNoSeMezclan() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long hotel = crearCategoria(ana, viajes, "Hotel");
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, viajes, cuentaViajes, "2026-01-05", -9_999, hotel);
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");

        assertThat(cifra(ana, viajes, ENERO, hotel, "actividad")).isEqualTo(-9_999);
        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "actividad")).isZero();
    }

    // ---------- disponible ----------

    @Test
    void elSaldoPositivoPasaAlMesSiguiente() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-10", -30_000, comida);
        asignar(ana, ana.presupuestoId, FEBRERO, comida, 50_000).andExpect(status().isOk());
        crearTx(ana, ana.presupuestoId, cuenta, "2026-02-10", -20_000, comida);

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "disponible")).isEqualTo(70_000);
        assertThat(cifra(ana, ana.presupuestoId, FEBRERO, comida, "disponible"))
                .isEqualTo(100_000);
        assertThat(cifra(ana, ana.presupuestoId, MARZO, comida, "disponible"))
                .isEqualTo(100_000);
    }

    @Test
    void elSobregastoNoSeArrastraALaCategoriaYSiBajaElListoDeLosMesesSiguientes()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-01", 500_000, null);
        asignar(ana, ana.presupuestoId, ENERO, comida, 20_000).andExpect(status().isOk());
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-15", -50_000, comida);
        asignar(ana, ana.presupuestoId, FEBRERO, comida, 10_000).andExpect(status().isOk());

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(480_000));
        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "disponible"))
                .isEqualTo(-30_000);
        assertThat(cuerpoFila(ana, ana.presupuestoId, ENERO, comida).get("sobregastada")
                .asBoolean()).isTrue();
        obtener(ana, ana.presupuestoId, FEBRERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(440_000));
        assertThat(cifra(ana, ana.presupuestoId, FEBRERO, comida, "disponible"))
                .isEqualTo(10_000);
        assertThat(cuerpoFila(ana, ana.presupuestoId, FEBRERO, comida).get("sobregastada")
                .asBoolean()).isFalse();
        obtener(ana, ana.presupuestoId, MARZO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(440_000));
    }

    @Test
    void unGastoSinAsignacionDejaLaCategoriaSobregastada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-10", -9_000, ocio);

        JsonNode fila = cuerpoFila(ana, ana.presupuestoId, ENERO, ocio);

        assertThat(fila.get("asignado").asLong()).isZero();
        assertThat(fila.get("actividad").asLong()).isEqualTo(-9_000);
        assertThat(fila.get("disponible").asLong()).isEqualTo(-9_000);
        assertThat(fila.get("sobregastada").asBoolean()).isTrue();
    }

    // ---------- listo para asignar ----------

    @Test
    void losIngresosSinCategoriaYLasAsignacionesDefinenElListo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-01", 500_000, null);

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(500_000));
        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        asignar(ana, ana.presupuestoId, ENERO, ocio, 20_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listoParaAsignar").value(380_000));
    }

    @Test
    void losSaldosInicialesPositivosDeCuentasDelPresupuestoSinTarjetaCuentan() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        crearCuenta(ana, ana.presupuestoId, "Corriente", "CORRIENTE", true, 100_000);
        crearCuenta(ana, ana.presupuestoId, "Tarjeta", "TARJETA_CREDITO", true, 50_000);
        crearCuenta(ana, ana.presupuestoId, "Seguimiento", "INVERSION", false, 70_000);
        crearCuenta(ana, ana.presupuestoId, "Deuda", "PRESTAMO", true, -20_000);

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(100_000));
        obtener(ana, ana.presupuestoId, "2030-01", false)
                .andExpect(jsonPath("$.listoParaAsignar").value(100_000));
    }

    @Test
    void losIngresosSeAcumulanPorFecha() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-05", 200_000, null);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-02-10", 80_000, null);

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(200_000));
        obtener(ana, ana.presupuestoId, FEBRERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(280_000));
    }

    @Test
    void loQueNoEsIngresoNoCambiaElListo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long banco = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        long fuera = crearCuenta(ana, ana.presupuestoId, "Seguimiento", "INVERSION", false, 0);
        crearTx(ana, ana.presupuestoId, banco, "2026-01-05", -30_000, null);
        crearTx(ana, ana.presupuestoId, banco, "2026-01-06", 9_000, comida);
        crearTx(ana, ana.presupuestoId, fuera, "2026-01-07", 70_000, null);
        Map<String, Object> dividida = cuerpoTx(banco, "2026-01-08", 6_000, null);
        dividida.put("subtransacciones", List.of(
                Map.of("categoriaId", comida, "monto", 4_000), Map.of("monto", 2_000)));
        enviar(post(rutaTransacciones(ana.presupuestoId)), ana, dividida)
                .andExpect(status().isCreated());

        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(0));
    }

    @Test
    void elListoPuedeSerNegativo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-01", 100_000, null);

        asignar(ana, ana.presupuestoId, ENERO, comida, 150_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listoParaAsignar").value(-50_000));
    }

    @Test
    void asignarEnUnMesFuturoSoloAfectaAEsteMesYLosPosteriores() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-05", 300_000, null);

        asignar(ana, ana.presupuestoId, "2026-06", comida, 120_000).andExpect(status().isOk());

        obtener(ana, ana.presupuestoId, "2026-06", false)
                .andExpect(jsonPath("$.listoParaAsignar").value(180_000));
        obtener(ana, ana.presupuestoId, ENERO, false)
                .andExpect(jsonPath("$.listoParaAsignar").value(300_000));
    }

    // ---------- mover dinero ----------

    @Test
    void moverDineroCambiaLosAsignadosYNoElListo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-01", 500_000, null);
        asignar(ana, ana.presupuestoId, ENERO, comida, 100_000).andExpect(status().isOk());
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-10", -30_000, comida);

        mover(ana, ana.presupuestoId, ENERO, comida, ocio, 30_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value(ENERO))
                .andExpect(jsonPath("$.listoParaAsignar").value(400_000));

        JsonNode deComida = cuerpoFila(ana, ana.presupuestoId, ENERO, comida);
        JsonNode deOcio = cuerpoFila(ana, ana.presupuestoId, ENERO, ocio);
        assertThat(deComida.get("asignado").asLong()).isEqualTo(70_000);
        assertThat(deComida.get("disponible").asLong()).isEqualTo(40_000);
        assertThat(deOcio.get("asignado").asLong()).isEqualTo(30_000);
        assertThat(deOcio.get("disponible").asLong()).isEqualTo(30_000);
    }

    @Test
    void moverTodoElDisponibleSePermiteYUnCentavoMasNo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());

        esperarReglaNegocio(mover(ana, ana.presupuestoId, ENERO, comida, ocio, 70_001));
        mover(ana, ana.presupuestoId, ENERO, comida, ocio, 70_000).andExpect(status().isOk());

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "disponible")).isZero();
        assertThat(cifra(ana, ana.presupuestoId, ENERO, ocio, "disponible")).isEqualTo(70_000);
    }

    @Test
    void moverDesdeUnOrigenSobregastadoDevuelve422() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        long cuenta = crearCuenta(ana, ana.presupuestoId, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ana.presupuestoId, cuenta, "2026-01-10", -5_000, comida);

        esperarReglaNegocio(mover(ana, ana.presupuestoId, ENERO, comida, ocio, 1));
    }

    @Test
    void moverConElMismoOrigenYDestinoDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());

        esperarDatosInvalidosSinCampos(
                mover(ana, ana.presupuestoId, ENERO, comida, comida, 1_000));
    }

    @Test
    void moverConMontoCeroONegativoOCamposAusentesDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());
        String ruta = ruta(ana.presupuestoId, ENERO) + "/mover-dinero";

        esperarDatosInvalidos(mover(ana, ana.presupuestoId, ENERO, comida, ocio, 0), "monto");
        esperarDatosInvalidos(mover(ana, ana.presupuestoId, ENERO, comida, ocio, -100), "monto");
        esperarDatosInvalidos(enviar(post(ruta), ana,
                Map.of("destinoId", ocio, "monto", 1)), "origenId");
        esperarDatosInvalidos(enviar(post(ruta), ana,
                Map.of("origenId", comida, "monto", 1)), "destinoId");
        esperarDatosInvalidos(enviar(post(ruta), ana,
                Map.of("origenId", comida, "destinoId", ocio)), "monto");
    }

    @Test
    void moverConCategoriaAjenaDevuelve404YNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long hotel = crearCategoria(ana, viajes, "Hotel");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());

        esperarNoEncontrado(mover(ana, ana.presupuestoId, ENERO, comida, hotel, 1_000));
        esperarNoEncontrado(mover(ana, ana.presupuestoId, ENERO, hotel, comida, 1_000));
        esperarNoEncontrado(mover(ana, ana.presupuestoId, ENERO, comida, 999_999, 1_000));

        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "asignado")).isEqualTo(70_000);
    }

    @Test
    void unMovimientoRechazadoNoDejaFilasNiCambios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());
        long filas = asignacionRepository.count();

        esperarReglaNegocio(mover(ana, ana.presupuestoId, ENERO, comida, ocio, 80_000));
        esperarDatosInvalidos(mover(ana, ana.presupuestoId, ENERO, comida, ocio, 0), "monto");
        esperarNoEncontrado(mover(ana, ana.presupuestoId, ENERO, comida, 999_999, 1));

        assertThat(asignacionRepository.count()).isEqualTo(filas);
        assertThat(cifra(ana, ana.presupuestoId, ENERO, comida, "asignado")).isEqualTo(70_000);
        assertThat(cifra(ana, ana.presupuestoId, ENERO, ocio, "asignado")).isZero();
    }

    // ---------- aislamiento ----------

    @Test
    void elPresupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long ocio = crearCategoria(ana, ana.presupuestoId, "Ocio");
        asignar(ana, ana.presupuestoId, ENERO, comida, 70_000).andExpect(status().isOk());
        long p = ana.presupuestoId;

        esperarNoEncontrado(obtener(beto, p, ENERO, false));
        esperarNoEncontrado(asignar(beto, p, ENERO, comida, 1));
        esperarNoEncontrado(mover(beto, p, ENERO, comida, ocio, 1));

        assertThat(cifra(ana, p, ENERO, comida, "asignado")).isEqualTo(70_000);
    }

    @Test
    void laCategoriaDeOtraPersonaDevuelve404PorLaUrlDelPropioPresupuesto() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long deAna = crearCategoria(ana, ana.presupuestoId, "Comida");
        long deBeto = crearCategoria(beto, beto.presupuestoId, "Propia");
        long p = beto.presupuestoId;

        esperarNoEncontrado(asignar(beto, p, ENERO, deAna, 1));
        esperarNoEncontrado(mover(beto, p, ENERO, deAna, deBeto, 1));
        esperarNoEncontrado(mover(beto, p, ENERO, deBeto, deAna, 1));
    }

    @Test
    void laCategoriaDeOtroPresupuestoDeLaMismaPersonaDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long viajes = idDe(crearPresupuesto(ana, "Viajes"));
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");
        long hotel = crearCategoria(ana, viajes, "Hotel");

        esperarNoEncontrado(asignar(ana, viajes, ENERO, comida, 1));
        esperarNoEncontrado(mover(ana, viajes, ENERO, comida, hotel, 1));
        esperarNoEncontrado(mover(ana, viajes, ENERO, hotel, comida, 1));
        asignar(ana, viajes, ENERO, hotel, 1).andExpect(status().isOk());
    }

    @Test
    void elPresupuestoOLaCategoriaInexistenteDevuelve404ConElCodigo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long comida = crearCategoria(ana, ana.presupuestoId, "Comida");

        esperarNoEncontrado(obtener(ana, 999_999L, ENERO, false));
        esperarNoEncontrado(asignar(ana, 999_999L, ENERO, comida, 1));
        esperarNoEncontrado(mover(ana, 999_999L, ENERO, comida, comida + 1, 1));
        esperarNoEncontrado(asignar(ana, ana.presupuestoId, ENERO, 999_999L, 1));
    }

    @Test
    void elPresupuestoAjenoGanaAlMesInvalido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");

        esperarNoEncontrado(obtener(beto, ana.presupuestoId, "basura", false));
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

    private long crearGrupo(Sesion sesion, long presupuestoId, String nombre) throws Exception {
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/grupos-categorias"),
                sesion, Map.of("nombre", nombre)));
    }

    private long crearCategoria(Sesion sesion, long presupuestoId, String nombre)
            throws Exception {
        long grupo = crearGrupo(sesion, presupuestoId, "Grupo " + nombre);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupo);
        cuerpo.put("nombre", nombre);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias"),
                sesion, cuerpo));
    }

    private ResultActions accionCategoria(
            Sesion sesion, long presupuestoId, long categoriaId, String accion) throws Exception {
        return accion(sesion, presupuestoId, "categorias", categoriaId, accion);
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

    private void crearTx(
            Sesion sesion, long presupuestoId, long cuentaId, String fecha, long monto,
            Long categoriaId) throws Exception {
        enviar(post(rutaTransacciones(presupuestoId)), sesion,
                cuerpoTx(cuentaId, fecha, monto, categoriaId))
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

    private ResultActions obtener(Sesion sesion, long presupuestoId, String mes, boolean ocultas)
            throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId, mes))
                .param("incluirOcultas", String.valueOf(ocultas))
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
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
        String cuerpo = obtener(sesion, presupuestoId, mes, ocultas)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private JsonNode cuerpoFila(Sesion sesion, long presupuestoId, String mes, long categoriaId)
            throws Exception {
        return buscarCategoria(cuerpoMes(sesion, presupuestoId, mes, true), categoriaId);
    }

    private long cifra(
            Sesion sesion, long presupuestoId, String mes, long categoriaId, String campo)
            throws Exception {
        return cuerpoFila(sesion, presupuestoId, mes, categoriaId).get(campo).asLong();
    }

    private static JsonNode buscarGrupo(JsonNode mes, long grupoId) {
        for (JsonNode grupo : mes.get("grupos")) {
            if (grupo.get("id").asLong() == grupoId) {
                return grupo;
            }
        }
        return null;
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
                .andReturn()
                .getResponse()
                .getContentAsString();
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
