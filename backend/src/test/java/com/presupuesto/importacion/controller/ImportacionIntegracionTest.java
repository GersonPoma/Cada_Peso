package com.presupuesto.importacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Flujos HTTP de la importación de CSV. Cada test crea sus propios usuarios y cuentas y solo
 * cuenta dentro de su presupuesto. El reloj de pruebas marca 2026-10-02: las fechas de los datos
 * son de septiembre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.presupuesto.comun.config.RelojDePruebaConfig.class)
@Transactional
class ImportacionIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";
    private static final String MES = "2026-09";
    private static final String ENCABEZADO = "Fecha;Descripcion;Monto;Memo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransaccionRepository transaccionRepository;

    // ---------- autenticación, aislamiento y orden de errores ----------

    @Test
    void sinTokenAmbasRutasDevuelven401() throws Exception {
        for (String sufijo : new String[] {"/vista-previa", ""}) {
            mockMvc.perform(multipart(ruta(1, 1) + sufijo)
                            .file(archivo(csv("05/09/2026;Cafe;-4,50;"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    @Test
    void presupuestoOCuentaAjenosDevuelven404SinCrearNadaNiRevelarNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long viajes = idDe(enviar(post(RUTA_PRESUPUESTOS), ana, Map.of("nombre", "Viajes")));
        long cuentaViajes = crearCuenta(ana, viajes, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv("05/09/2026;Cafe;-4,50;");

        for (long[] par : new long[][] {
                {ana.presupuestoId, 999_999L},
                {viajes, banco},
                {ana.presupuestoId, cuentaViajes},
                {999_999L, banco}}) {
            esperarNoEncontrado(vistaPrevia(ana, par[0], par[1], contenido, mapeo()));
            esperarNoEncontrado(importar(ana, par[0], par[1], contenido, mapeo()));
        }
        esperarNoEncontrado(vistaPrevia(beto, ana.presupuestoId, banco, contenido, mapeo()));
        esperarNoEncontrado(importar(beto, ana.presupuestoId, banco, contenido, mapeo()));
        assertThat(transacciones(ana, banco)).isEmpty();
    }

    @Test
    void cuentaInexistenteConArchivoOParametrosInvalidosDevuelve404NoFalta400()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Map<String, String> malos = mapeo();
        malos.put("omitirInvalidas", "quizas");
        malos.put("tieneEncabezado", "quizas");

        esperarNoEncontrado(vistaPrevia(ana, ana.presupuestoId, 999_999L, new byte[0], malos));
        esperarNoEncontrado(importar(ana, ana.presupuestoId, 999_999L, new byte[0], malos));
        esperarNoEncontrado(sinArchivo("/vista-previa", ana, ana.presupuestoId, 999_999L, malos));
        esperarNoEncontrado(sinArchivo("", ana, ana.presupuestoId, 999_999L, malos));
    }

    @Test
    void despuesDeLosNotFoundElArchivoYLosParametrosDan400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco, new byte[0], mapeo()));
        esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, new byte[0], mapeo()));
        esperarDatosInvalidos(
                vistaPrevia(ana, ana.presupuestoId, banco, csv("x"), Map.of()));
        esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, csv("x"), Map.of()));
    }

    // ---------- vista previa ----------

    @Test
    void laVistaPreviaInterpretaClasificaYNoGuardaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, banco, "2026-09-05", -4_500, "Cafe Luna");
        long beneficiariosAntes = beneficiarios(ana);

        vistaPrevia(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Cafe Luna;-4,50;desayuno",
                        "06/09/2026;Panaderia;-1.200,5;",
                        "31/02/2026;Fecha mala;-1,00;"), mapeo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.total").value(3))
                .andExpect(jsonPath("$.totales.nuevas").value(1))
                .andExpect(jsonPath("$.totales.duplicadas").value(1))
                .andExpect(jsonPath("$.totales.invalidas").value(1))
                .andExpect(jsonPath("$.totales.sinCategoria").value(1))
                .andExpect(jsonPath("$.filas[0].fila").value(1))
                .andExpect(jsonPath("$.filas[0].fecha").value("2026-09-05"))
                .andExpect(jsonPath("$.filas[0].monto").value(-4_500))
                .andExpect(jsonPath("$.filas[0].beneficiario").value("Cafe Luna"))
                .andExpect(jsonPath("$.filas[0].memo").value("desayuno"))
                .andExpect(jsonPath("$.filas[0].estado").value("DUPLICADA"))
                .andExpect(jsonPath("$.filas[0].motivo").doesNotExist())
                .andExpect(jsonPath("$.filas[1].monto").value(-1_200_500))
                .andExpect(jsonPath("$.filas[1].memo").doesNotExist())
                .andExpect(jsonPath("$.filas[1].estado").value("NUEVA"))
                .andExpect(jsonPath("$.filas[2].estado").value("INVALIDA"))
                .andExpect(jsonPath("$.filas[2].motivo").value("La fecha no es válida"));

        assertThat(transacciones(ana, banco)).hasSize(1);
        assertThat(beneficiarios(ana)).isEqualTo(beneficiariosAntes);
    }

    @Test
    void unArchivoSoloConEncabezadoDa200ConTodoEnCero() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        vistaPrevia(ana, ana.presupuestoId, banco, csv(), mapeo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.total").value(0))
                .andExpect(jsonPath("$.totales.nuevas").value(0))
                .andExpect(jsonPath("$.totales.sinCategoria").value(0))
                .andExpect(jsonPath("$.filas.length()").value(0));
        importar(ana, ana.presupuestoId, banco, csv(), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(0))
                .andExpect(jsonPath("$.duplicadas").value(0))
                .andExpect(jsonPath("$.omitidas").value(0))
                .andExpect(jsonPath("$.sinCategoria").value(0));
    }

    // ---------- importación ----------

    @Test
    void importaLasFilasNuevasSinCategoriaNoConciliadasYSinAprobar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        importar(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Cafe Luna;-4,50;desayuno",
                        "06/09/2026;Sueldo;1.000,00;",
                        "07/09/2026;;-2,00;sin descripcion"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(3))
                .andExpect(jsonPath("$.duplicadas").value(0))
                .andExpect(jsonPath("$.omitidas").value(0))
                .andExpect(jsonPath("$.sinCategoria").value(3));

        JsonNode creadas = transacciones(ana, banco);
        assertThat(creadas).hasSize(3);
        for (JsonNode t : creadas) {
            assertThat(t.get("estado").asString()).isEqualTo("NO_CONCILIADA");
            assertThat(t.get("aprobada").asBoolean()).isFalse();
            assertThat(t.get("categoriaId").isNull()).isTrue();
            assertThat(t.get("subtransacciones")).isEmpty();
        }
        JsonNode cafe = porFecha(creadas, "2026-09-05");
        assertThat(cafe.get("monto").asLong()).isEqualTo(-4_500);
        assertThat(cafe.get("beneficiario").asString()).isEqualTo("Cafe Luna");
        assertThat(cafe.get("memo").asString()).isEqualTo("desayuno");
        assertThat(porFecha(creadas, "2026-09-06").get("monto").asLong()).isEqualTo(1_000_000);
        JsonNode sinDescripcion = porFecha(creadas, "2026-09-07");
        assertThat(sinDescripcion.get("beneficiario").isNull()).isTrue();
        assertThat(sinDescripcion.get("beneficiarioId").isNull()).isTrue();
    }

    @Test
    void elBeneficiarioExistenteSeReutilizaSinDistinguirMayusculasYElNuevoSeCrea()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, banco, "2026-09-01", -1_000, "Cafe Luna");
        long antes = beneficiarios(ana);

        importar(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;CAFE LUNA;-4,50;",
                        "06/09/2026;Panaderia;-1,00;",
                        "07/09/2026;PANADERIA;-2,00;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(3));

        assertThat(beneficiarios(ana)).isEqualTo(antes + 1);
        JsonNode todas = transacciones(ana, banco);
        long luna = porFecha(todas, "2026-09-01").get("beneficiarioId").asLong();
        assertThat(porFecha(todas, "2026-09-05").get("beneficiarioId").asLong()).isEqualTo(luna);
        assertThat(porFecha(todas, "2026-09-05").get("beneficiario").asString())
                .isEqualTo("Cafe Luna");
        assertThat(porFecha(todas, "2026-09-06").get("beneficiarioId").asLong())
                .isEqualTo(porFecha(todas, "2026-09-07").get("beneficiarioId").asLong());
    }

    @Test
    void unaCuentaFueraDelPresupuestoYUnaTarjetaAdmitenLaImportacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long externa = crearCuenta(ana, "Efectivo externo", "CORRIENTE", false, 0);
        long tarjeta = crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0);

        importar(ana, ana.presupuestoId, externa, csv("05/09/2026;Cafe;-4,50;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1));
        importar(ana, ana.presupuestoId, tarjeta, csv("05/09/2026;Cafe;-4,50;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1));
    }

    @Test
    void reimportarElMismoArchivoCreaCero() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv(
                "05/09/2026;Cafe Luna;-4,50;",
                "05/09/2026;Cafe Luna;-4,50;",
                "06/09/2026;Sueldo;100,00;");

        importar(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(3));
        vistaPrevia(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(jsonPath("$.totales.nuevas").value(0))
                .andExpect(jsonPath("$.totales.duplicadas").value(3));
        importar(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(0))
                .andExpect(jsonPath("$.duplicadas").value(3))
                .andExpect(jsonPath("$.omitidas").value(0))
                .andExpect(jsonPath("$.sinCategoria").value(0));

        assertThat(transacciones(ana, banco)).hasSize(3);
    }

    @Test
    void dosFilasIgualesEnCuentaVaciaSeCreanLasDos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        importar(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Cafe Luna;-4,50;", "05/09/2026;Cafe Luna;-4,50;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(2))
                .andExpect(jsonPath("$.duplicadas").value(0));
    }

    @Test
    void laCuentaConUnaYElArchivoConDosCreaSoloUna() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, banco, "2026-09-05", -4_500, "cafe luna");

        importar(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Cafe Luna;-4,50;", "05/09/2026;Cafe Luna;-4,50;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1))
                .andExpect(jsonPath("$.duplicadas").value(1));

        assertThat(transacciones(ana, banco)).hasSize(2);
    }

    @Test
    void laImportacionNoConfiaEnLaVistaPreviaAnterior() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv("05/09/2026;Cafe Luna;-4,50;");

        vistaPrevia(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(jsonPath("$.filas[0].estado").value("NUEVA"));
        crearTx(ana, banco, "2026-09-05", -4_500, "Cafe Luna");

        importar(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(0))
                .andExpect(jsonPath("$.duplicadas").value(1));
    }

    @Test
    void otraCuentaYOtroPresupuestoNoCuentanComoDuplicados() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long ahorros = crearCuenta(ana, "Ahorros", "AHORRO", true, 0);
        long cuentaBeto = crearCuenta(beto, "Banco", "CORRIENTE", true, 0);
        crearTx(ana, ahorros, "2026-09-05", -4_500, "Cafe Luna");
        crearTx(beto, cuentaBeto, "2026-09-05", -4_500, "Cafe Luna");

        vistaPrevia(ana, ana.presupuestoId, banco, csv("05/09/2026;Cafe Luna;-4,50;"), mapeo())
                .andExpect(jsonPath("$.totales.nuevas").value(1))
                .andExpect(jsonPath("$.totales.duplicadas").value(0));
        importar(ana, ana.presupuestoId, banco, csv("05/09/2026;Cafe Luna;-4,50;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1));

        assertThat(transacciones(ana, ahorros)).hasSize(1);
        assertThat(transacciones(beto, cuentaBeto)).hasSize(1);
    }

    @Test
    void unBeneficiarioDe150CaracteresSeTruncaAntesDeLaClaveYLaSegundaImportacionEsDuplicada()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv("05/09/2026;" + "ñ".repeat(150) + ";-4,50;");

        importar(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1));
        assertThat(transacciones(ana, banco).get(0).get("beneficiario").asString())
                .isEqualTo("ñ".repeat(100));

        importar(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(0))
                .andExpect(jsonPath("$.duplicadas").value(1));
        assertThat(transacciones(ana, banco)).hasSize(1);
    }

    @Test
    void elMemoLargoSeTruncaA500() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        importar(ana, ana.presupuestoId, banco,
                csv("05/09/2026;Cafe;-4,50;" + "m".repeat(800)), mapeo())
                .andExpect(status().isCreated());

        assertThat(transacciones(ana, banco).get(0).get("memo").asString()).hasSize(500);
    }

    // ---------- filas inválidas ----------

    @Test
    void conUnaFilaInvalidaSinOmitirResponde422YNoCreaNadaNiBeneficiarios() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long antes = beneficiarios(ana);

        esperarReglaNegocio(importar(ana, ana.presupuestoId, banco, csv(
                "05/09/2026;Cafe Luna;-4,50;",
                "06/09/2026;Panaderia;-1,00;",
                "ayer;Mala;-1,00;"), mapeo()));

        assertThat(transacciones(ana, banco)).isEmpty();
        assertThat(beneficiarios(ana)).isEqualTo(antes);
    }

    @Test
    void conOmitirInvalidasCreaLasNuevasYCuentaLasOmitidas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        Map<String, String> omitiendo = mapeo();
        omitiendo.put("omitirInvalidas", "true");

        importar(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Cafe Luna;-4,50;",
                        "06/09/2026;Panaderia;-1,00;",
                        "ayer;Mala;-1,00;"), omitiendo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(2))
                .andExpect(jsonPath("$.duplicadas").value(0))
                .andExpect(jsonPath("$.omitidas").value(1))
                .andExpect(jsonPath("$.sinCategoria").value(2));

        assertThat(transacciones(ana, banco)).hasSize(2);
    }

    @Test
    void unMontoCeroInvalidaSoloEsaFila() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv("05/09/2026;Cafe;0,00;", "06/09/2026;Pan;-1,00;");

        vistaPrevia(ana, ana.presupuestoId, banco, contenido, mapeo())
                .andExpect(jsonPath("$.filas[0].estado").value("INVALIDA"))
                .andExpect(jsonPath("$.filas[0].motivo").value("El monto no puede ser 0"))
                .andExpect(jsonPath("$.filas[1].estado").value("NUEVA"));
        esperarReglaNegocio(importar(ana, ana.presupuestoId, banco, contenido, mapeo()));
    }

    // ---------- cuenta cerrada ----------

    @Test
    void conLaCuentaCerradaAmbosEndpointsDan422DeCuentaCerrada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        cerrarCuenta(ana, banco);

        for (byte[] contenido : new byte[][] {
                csv("05/09/2026;Cafe;-4,50;"),
                csv("ayer;Mala;-1,00;", "tambien;mala;x;")}) {
            esperarCuentaCerrada(vistaPrevia(ana, ana.presupuestoId, banco, contenido, mapeo()));
            esperarCuentaCerrada(importar(ana, ana.presupuestoId, banco, contenido, mapeo()));
            Map<String, String> omitiendo = mapeo();
            omitiendo.put("omitirInvalidas", "true");
            esperarCuentaCerrada(importar(ana, ana.presupuestoId, banco, contenido, omitiendo));
        }
        assertThat(transacciones(ana, banco)).isEmpty();
    }

    @Test
    void conLaCuentaCerradaUnArchivoInvalidoSigueSiendo400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        cerrarCuenta(ana, banco);
        byte[] iso = ("05/09/2026;Café;-4,50;").getBytes(StandardCharsets.ISO_8859_1);

        esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco, iso, mapeo()));
        esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, iso, mapeo()));
        esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco, new byte[0], mapeo()));
    }

    // ---------- booleanos ----------

    @Test
    void unBooleanoQueNoLoEsDa400ConCuentaExistenteY404ConCuentaInexistente() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] contenido = csv("05/09/2026;Cafe;-4,50;");

        for (String nombre : new String[] {"omitirInvalidas", "tieneEncabezado"}) {
            Map<String, String> malos = mapeo();
            malos.put(nombre, "quizas");
            esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, contenido, malos));
            if (nombre.equals("tieneEncabezado")) {
                esperarDatosInvalidos(
                        vistaPrevia(ana, ana.presupuestoId, banco, contenido, malos));
            }
            esperarNoEncontrado(importar(ana, ana.presupuestoId, 999_999L, contenido, malos));
            esperarNoEncontrado(vistaPrevia(ana, ana.presupuestoId, 999_999L, contenido, malos));
        }
        assertThat(transacciones(ana, banco)).isEmpty();
    }

    // ---------- límites y codificación ----------

    @Test
    void unArchivoMayorQueElLimiteDeLaAplicacionDa400ConElMaximoEnElMensaje() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] justoPasado = relleno(2 * 1024 * 1024 + 1);
        byte[] tresMb = relleno(3 * 1024 * 1024);

        for (byte[] grande : new byte[][] {justoPasado, tresMb}) {
            esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco, grande, mapeo()))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("2 MB")));
            esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, grande, mapeo()))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("2 MB")));
        }
        assertThat(transacciones(ana, banco)).isEmpty();
    }

    @Test
    void masFilasQueElMaximoDa400YExactamenteElMaximoSeImporta() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long antes = transaccionRepository.count();

        esperarDatosInvalidos(vistaPrevia(
                ana, ana.presupuestoId, banco, filasDistintas(5_001), mapeo()));
        esperarDatosInvalidos(importar(
                ana, ana.presupuestoId, banco, filasDistintas(5_001), mapeo()));
        assertThat(transaccionRepository.count()).isEqualTo(antes);

        importar(ana, ana.presupuestoId, banco, filasDistintas(5_000), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(5_000))
                .andExpect(jsonPath("$.sinCategoria").value(5_000));
        assertThat(transaccionRepository.count()).isEqualTo(antes + 5_000);
    }

    @Test
    void unaCodificacionQueNoEsUtf8Da400QueMencionaUtf8() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] iso = (ENCABEZADO + "\n05/09/2026;Café;-4,50;\n")
                .getBytes(StandardCharsets.ISO_8859_1);

        for (ResultActions resultado : new ResultActions[] {
                vistaPrevia(ana, ana.presupuestoId, banco, iso, mapeo()),
                importar(ana, ana.presupuestoId, banco, iso, mapeo())}) {
            esperarDatosInvalidos(resultado)
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("UTF-8")));
        }
        assertThat(transacciones(ana, banco)).isEmpty();
    }

    @Test
    void utf8ConBomSeAceptaYElBomNoEnsuciaElEncabezado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] conEncabezado = concatenar(bom, csv("05/09/2026;Café;-4,50;"));
        byte[] soloFila = concatenar(bom, "05/09/2026;Café;-4,50;".getBytes(StandardCharsets.UTF_8));
        Map<String, String> sinEncabezado = mapeo();
        sinEncabezado.put("tieneEncabezado", "false");

        // Sin encabezado, un BOM sin descartar quedaría pegado a la fecha y invalidaría la fila.
        vistaPrevia(ana, ana.presupuestoId, banco, conEncabezado, mapeo())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.nuevas").value(1));
        vistaPrevia(ana, ana.presupuestoId, banco, soloFila, sinEncabezado)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.nuevas").value(1))
                .andExpect(jsonPath("$.filas[0].beneficiario").value("Café"));
    }

    @Test
    void comillaSinCerrarYArchivoVacioDan400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco,
                csv("05/09/2026;\"Cafe;-4,50;"), mapeo()));
        esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco,
                csv("05/09/2026;\"Cafe;-4,50;"), mapeo()));
        esperarDatosInvalidos(vistaPrevia(ana, ana.presupuestoId, banco, new byte[0], mapeo()));
        esperarDatosInvalidos(importar(ana, ana.presupuestoId, banco, new byte[0], mapeo()));
    }

    @Test
    void faltarLaParteArchivoDa400EnAmbosEndpoints() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);

        esperarDatosInvalidos(sinArchivo("/vista-previa", ana, ana.presupuestoId, banco, mapeo()));
        esperarDatosInvalidos(sinArchivo("", ana, ana.presupuestoId, banco, mapeo()));
    }

    // ---------- mapeo ----------

    @Test
    void montoEnDosColumnasDebitoYCredito() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        Map<String, String> p = mapeo();
        p.remove("columnaMonto");
        p.put("columnaDebito", "2");
        p.put("columnaCredito", "3");
        p.remove("columnaMemo");

        vistaPrevia(ana, ana.presupuestoId, banco, csv(
                        "05/09/2026;Super;45,00;",
                        "06/09/2026;Sueldo;;1.000,00",
                        "07/09/2026;Raro;10,00;5,00",
                        "08/09/2026;Cero;;"), p)
                .andExpect(jsonPath("$.filas[0].monto").value(-45_000))
                .andExpect(jsonPath("$.filas[1].monto").value(1_000_000))
                .andExpect(jsonPath("$.filas[2].estado").value("INVALIDA"))
                .andExpect(jsonPath("$.filas[3].estado").value("INVALIDA"));
    }

    @Test
    void tabuladorYDecimalPuntoSinMiles() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        Map<String, String> p = new LinkedHashMap<>();
        p.put("separador", "TABULADOR");
        p.put("columnaFecha", "0");
        p.put("formatoFecha", "yyyy-MM-dd");
        p.put("columnaMonto", "1");
        p.put("separadorDecimal", "PUNTO");
        p.put("columnaDescripcion", "2");

        importar(ana, ana.presupuestoId, banco,
                "2026-09-05\t-1234.5\tCafe\n".getBytes(StandardCharsets.UTF_8), p)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(1));

        assertThat(transacciones(ana, banco).get(0).get("monto").asLong()).isEqualTo(-1_234_500);
    }

    @Test
    void cadaFormatoDeFechaSeLeeConYSinEncabezado() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        String[][] formatos = {
            {"yyyy-MM-dd", "2026-09-05"},
            {"dd/MM/yyyy", "05/09/2026"},
            {"MM/dd/yyyy", "09/05/2026"},
            {"dd-MM-yyyy", "05-09-2026"}};

        for (String[] formato : formatos) {
            for (boolean encabezado : new boolean[] {true, false}) {
                Map<String, String> p = mapeo();
                p.put("formatoFecha", formato[0]);
                p.put("tieneEncabezado", Boolean.toString(encabezado));
                String datos = formato[1] + ";Cafe;-4,50;\n";
                byte[] contenido = ((encabezado ? ENCABEZADO + "\n" : "") + datos)
                        .getBytes(StandardCharsets.UTF_8);

                vistaPrevia(ana, ana.presupuestoId, banco, contenido, p)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totales.total").value(1))
                        .andExpect(jsonPath("$.filas[0].fecha").value("2026-09-05"));
            }
        }
    }

    // ---------- listo para asignar ----------

    @Test
    void importarEntradasSinCategoriaInflaElListoParaAsignarHastaCategorizarLosGastos()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long banco = crearCuenta(ana, "Banco", "CORRIENTE", true, 0);
        long comida = crearCategoria(ana, "Comida de prueba");
        JsonNode antes = mes(ana);

        importar(ana, ana.presupuestoId, banco, csv(
                        "03/09/2026;Sueldo;5.000,00;", "03/09/2026;Super;-3.000,00;"), mapeo())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importadas").value(2))
                .andExpect(jsonPath("$.sinCategoria").value(2));

        JsonNode despues = mes(ana);
        long listo = despues.get("listoParaAsignar").asLong();
        long disponibles = despues.get("totalDisponible").asLong();
        assertThat(listo).isEqualTo(antes.get("listoParaAsignar").asLong() + 5_000_000);
        assertThat(disponibles).isEqualTo(antes.get("totalDisponible").asLong());
        long dinero = dineroEnCuentas(ana);
        assertThat(dinero).isEqualTo(2_000_000);
        assertThat(dinero).isNotEqualTo(listo + disponibles);

        JsonNode super_ = porFecha(transacciones(ana, banco), "2026-09-03", -3_000_000);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fecha", "2026-09-03");
        cuerpo.put("monto", -3_000_000);
        cuerpo.put("categoriaId", comida);
        cuerpo.put("beneficiario", "Super");
        enviar(put(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId + "/transacciones/"
                + super_.get("id").asLong()), ana, cuerpo).andExpect(status().isOk());

        JsonNode categorizado = mes(ana);
        assertThat(categorizado.get("listoParaAsignar").asLong()).isEqualTo(listo);
        assertThat(categorizado.get("totalDisponible").asLong()).isEqualTo(disponibles - 3_000_000);
        assertThat(disponible(categorizado, comida)).isEqualTo(-3_000_000);
        assertThat(dineroEnCuentas(ana)).isEqualTo(
                categorizado.get("listoParaAsignar").asLong()
                        + categorizado.get("totalDisponible").asLong());
    }

    @Test
    void unaTarjetaOUnaCuentaFueraDelPresupuestoNoInflanElListoParaAsignar() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long tarjeta = crearCuenta(ana, "Visa", "TARJETA_CREDITO", true, 0);
        long externa = crearCuenta(ana, "Efectivo externo", "CORRIENTE", false, 0);
        long listo = mes(ana).get("listoParaAsignar").asLong();

        importar(ana, ana.presupuestoId, tarjeta, csv("03/09/2026;Pago;1.000,00;"), mapeo())
                .andExpect(status().isCreated());
        importar(ana, ana.presupuestoId, externa, csv("03/09/2026;Regalo;1.000,00;"), mapeo())
                .andExpect(status().isCreated());

        assertThat(mes(ana).get("listoParaAsignar").asLong()).isEqualTo(listo);
    }

    // ---------- utilidades ----------

    private record Sesion(String token, long presupuestoId) {
    }

    private static Map<String, String> mapeo() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("separador", "PUNTO_Y_COMA");
        p.put("tieneEncabezado", "true");
        p.put("columnaFecha", "0");
        p.put("formatoFecha", "dd/MM/yyyy");
        p.put("columnaMonto", "2");
        p.put("separadorDecimal", "COMA");
        p.put("separadorMiles", "PUNTO");
        p.put("columnaDescripcion", "1");
        p.put("columnaMemo", "3");
        return p;
    }

    /** Encabezado más las líneas, en UTF-8. */
    private static byte[] csv(String... lineas) {
        StringBuilder contenido = new StringBuilder(ENCABEZADO).append('\n');
        for (String linea : lineas) {
            contenido.append(linea).append('\n');
        }
        return contenido.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Filas válidas y todas distintas (la fecha, el monto y la descripción varían). */
    private static byte[] filasDistintas(int cantidad) {
        StringBuilder contenido = new StringBuilder(ENCABEZADO).append('\n');
        for (int i = 0; i < cantidad; i++) {
            contenido.append(String.format("%02d/09/2026", 1 + i % 28))
                    .append(";Comercio ").append(i % 50)
                    .append(";-").append(i + 1).append(",00;\n");
        }
        return contenido.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatenar(byte[] primero, byte[] segundo) {
        byte[] junto = new byte[primero.length + segundo.length];
        System.arraycopy(primero, 0, junto, 0, primero.length);
        System.arraycopy(segundo, 0, junto, primero.length, segundo.length);
        return junto;
    }

    private static byte[] relleno(int bytes) {
        byte[] datos = new byte[bytes];
        java.util.Arrays.fill(datos, (byte) 'a');
        return datos;
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "extracto.csv", "text/csv", contenido);
    }

    private ResultActions subir(
            String sufijo, Sesion sesion, long presupuestoId, long cuentaId, byte[] contenido,
            Map<String, String> parametros) throws Exception {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart(ruta(presupuestoId, cuentaId) + sufijo).file(archivo(contenido));
        parametros.forEach(peticion::param);
        return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions sinArchivo(
            String sufijo, Sesion sesion, long presupuestoId, long cuentaId,
            Map<String, String> parametros) throws Exception {
        MockMultipartHttpServletRequestBuilder peticion =
                multipart(ruta(presupuestoId, cuentaId) + sufijo);
        parametros.forEach(peticion::param);
        return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)));
    }

    private ResultActions vistaPrevia(
            Sesion sesion, long presupuestoId, long cuentaId, byte[] contenido,
            Map<String, String> parametros) throws Exception {
        return subir("/vista-previa", sesion, presupuestoId, cuentaId, contenido, parametros);
    }

    private ResultActions importar(
            Sesion sesion, long presupuestoId, long cuentaId, byte[] contenido,
            Map<String, String> parametros) throws Exception {
        return subir("", sesion, presupuestoId, cuentaId, contenido, parametros);
    }

    /** Transacciones de la cuenta (hasta 100), de la más reciente a la más antigua. */
    private JsonNode transacciones(Sesion sesion, long cuentaId) throws Exception {
        return leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                        + "/transacciones")
                        .param("cuentaId", Long.toString(cuentaId))
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())).get("contenido");
    }

    private long beneficiarios(Sesion sesion) throws Exception {
        return leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                        + "/beneficiarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk())).size();
    }

    private static JsonNode porFecha(JsonNode transacciones, String fecha) {
        for (JsonNode t : transacciones) {
            if (t.get("fecha").asString().equals(fecha)) {
                return t;
            }
        }
        throw new AssertionError("No hay transacción del " + fecha);
    }

    private static JsonNode porFecha(JsonNode transacciones, String fecha, long monto) {
        for (JsonNode t : transacciones) {
            if (t.get("fecha").asString().equals(fecha) && t.get("monto").asLong() == monto) {
                return t;
            }
        }
        throw new AssertionError("No hay transacción del " + fecha + " por " + monto);
    }

    private JsonNode mes(Sesion sesion) throws Exception {
        return leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                        + "/meses/" + MES)
                        .param("incluirOcultas", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk()));
    }

    private static long disponible(JsonNode mes, long categoriaId) {
        for (JsonNode grupo : mes.get("grupos")) {
            for (JsonNode categoria : grupo.get("categorias")) {
                if (categoria.get("categoriaId").asLong() == categoriaId) {
                    return categoria.get("disponible").asLong();
                }
            }
        }
        throw new AssertionError("No hay categoría " + categoriaId);
    }

    private long dineroEnCuentas(Sesion sesion) throws Exception {
        long total = 0;
        JsonNode saldos = leer(mockMvc.perform(get(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                        + "/transacciones/saldos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token)))
                .andExpect(status().isOk()));
        for (JsonNode saldo : saldos) {
            total += saldo.get("saldo").asLong();
        }
        return total;
    }

    private long crearTx(Sesion sesion, long cuentaId, String fecha, long monto,
            String beneficiario) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cuentaId", cuentaId);
        cuerpo.put("fecha", fecha);
        cuerpo.put("monto", monto);
        cuerpo.put("beneficiario", beneficiario);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId
                + "/transacciones"), sesion, cuerpo));
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

    private long crearCategoria(Sesion sesion, String nombre) throws Exception {
        long grupo = idDe(enviar(
                post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/grupos-categorias"),
                sesion, Map.of("nombre", "Grupo " + nombre)));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupo);
        cuerpo.put("nombre", nombre);
        return idDe(enviar(post(RUTA_PRESUPUESTOS + "/" + sesion.presupuestoId + "/categorias"),
                sesion, cuerpo));
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
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/cuentas/" + cuentaId + "/importacion";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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

    private static void esperarCuentaCerrada(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andExpect(jsonPath("$.detail").value("La cuenta está cerrada"));
    }

    private static ResultActions esperarDatosInvalidos(ResultActions resultado) throws Exception {
        return resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }
}
