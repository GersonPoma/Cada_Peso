package com.presupuesto.categoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaIntegracionTest {

    private static final String RUTA_PRESUPUESTOS = "/api/v1/presupuestos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- autenticación ----------

    @Test
    void sinTokenTodasLasRutasDevuelven401() throws Exception {
        String ruta = ruta(1);
        String cuerpo = "{\"grupoId\": 1, \"nombre\": \"Luz\", \"posicion\": 0}";

        esperarNoAutenticado(mockMvc.perform(get(ruta)));
        esperarNoAutenticado(mockMvc.perform(get(ruta + "/1")));
        esperarNoAutenticado(mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(put(ruta + "/1")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/ocultar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/mostrar")));
        esperarNoAutenticado(mockMvc.perform(post(ruta + "/1/mover")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)));
    }

    // ---------- crear y validar ----------

    @Test
    void crearDevuelve201ConOrdenCeroVisibleYSinNota() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        crear(ana, ana.presupuestoId, grupo, "Alquiler", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.grupoId").value(grupo))
                .andExpect(jsonPath("$.nombre").value("Alquiler"))
                .andExpect(jsonPath("$.orden").value(0))
                .andExpect(jsonPath("$.oculta").value(false))
                .andExpect(jsonPath("$.nota").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()))
                .andExpect(jsonPath("$.fechaActualizacion", notNullValue()));
    }

    @Test
    void elOrdenAlCrearEsConsecutivoPorGrupo() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");

        crear(ana, ana.presupuestoId, x, "Uno", null).andExpect(jsonPath("$.orden").value(0));
        crear(ana, ana.presupuestoId, x, "Dos", null).andExpect(jsonPath("$.orden").value(1));
        crear(ana, ana.presupuestoId, y, "Tres", null).andExpect(jsonPath("$.orden").value(0));
        crear(ana, ana.presupuestoId, x, "Cuatro", null)
                .andExpect(jsonPath("$.orden").value(2));
    }

    @Test
    void laNotaSeRecortaYUnaNotaVaciaSeGuardaComoNula() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        crear(ana, ana.presupuestoId, grupo, "  Alquiler  ", "  Pago mensual  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Alquiler"))
                .andExpect(jsonPath("$.nota").value("Pago mensual"));
        crear(ana, ana.presupuestoId, grupo, "Luz", "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(org.hamcrest.Matchers.nullValue()));
        crear(ana, ana.presupuestoId, grupo, "Agua", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void elLimiteDeLaNotaEsDeQuinientosCaracteres() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        crear(ana, ana.presupuestoId, grupo, "Alquiler", "n".repeat(500))
                .andExpect(status().isCreated());
        esperarDatosInvalidos(
                crear(ana, ana.presupuestoId, grupo, "Luz", "n".repeat(501)), "nota");
    }

    @Test
    void unNombreInvalidoOUnGrupoAusenteDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        esperarDatosInvalidos(crear(ana, ana.presupuestoId, grupo, "   ", null), "nombre");
        esperarDatosInvalidos(crear(ana, ana.presupuestoId, grupo, "a".repeat(101), null),
                "nombre");
        esperarDatosInvalidos(enviar(ana, ruta(ana.presupuestoId), Map.of("nombre", "Luz")),
                "grupoId");
        crear(ana, ana.presupuestoId, grupo, "a".repeat(100), null)
                .andExpect(status().isCreated());
    }

    // ---------- duplicados ----------

    @Test
    void unNombreRepetidoEnElGrupoSinDistinguirMayusculasDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        crear(ana, ana.presupuestoId, grupo, "Alquiler", null).andExpect(status().isCreated());

        crear(ana, ana.presupuestoId, grupo, "ALQUILER", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_YA_EXISTE"));
    }

    @Test
    void elMismoNombreEnGruposDistintosSePermite() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        crear(ana, ana.presupuestoId, x, "Otros", null).andExpect(status().isCreated());

        crear(ana, ana.presupuestoId, y, "Otros", null).andExpect(status().isCreated());
    }

    @Test
    void editarAUnNombreExistenteDelGrupoDevuelve409() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        crear(ana, ana.presupuestoId, grupo, "Alquiler", null);
        long luz = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));

        editar(ana, ana.presupuestoId, luz, cuerpoEdicion("alquiler", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_YA_EXISTE"));
    }

    @Test
    void editarCambiandoSoloLaCapitalizacionEsValido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "alquiler", null));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Alquiler", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Alquiler"));
    }

    // ---------- editar y consultar ----------

    @Test
    void editarCambiaNombreYNotaYConservaGrupoYOrden() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        crear(ana, ana.presupuestoId, grupo, "Primera", null);
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", "Vieja"));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Electricidad", "  Nueva  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Electricidad"))
                .andExpect(jsonPath("$.nota").value("Nueva"))
                .andExpect(jsonPath("$.grupoId").value(grupo))
                .andExpect(jsonPath("$.orden").value(1));
    }

    @Test
    void editarSinNotaBorraLaNota() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", "Vieja"));

        editar(ana, ana.presupuestoId, id, cuerpoEdicion("Luz", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void editarIgnoraGrupoIdYOrden() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        crear(ana, ana.presupuestoId, x, "Primera", null);
        long id = idDe(crear(ana, ana.presupuestoId, x, "Luz", null));
        Map<String, Object> cuerpo = cuerpoEdicion("Luz2", null);
        cuerpo.put("grupoId", y);
        cuerpo.put("orden", 7);

        editar(ana, ana.presupuestoId, id, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupoId").value(x))
                .andExpect(jsonPath("$.orden").value(1));
    }

    @Test
    void editarConDatosInvalidosDevuelve400() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));

        esperarDatosInvalidos(editar(ana, ana.presupuestoId, id, cuerpoEdicion("  ", null)),
                "nombre");
        esperarDatosInvalidos(
                editar(ana, ana.presupuestoId, id, cuerpoEdicion("Luz", "n".repeat(501))),
                "nota");
    }

    @Test
    void obtenerDevuelveElDetalle() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", "Mensual"));

        obtener(ana, ana.presupuestoId, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.grupoId").value(grupo))
                .andExpect(jsonPath("$.nombre").value("Luz"))
                .andExpect(jsonPath("$.orden").value(0))
                .andExpect(jsonPath("$.oculta").value(false))
                .andExpect(jsonPath("$.nota").value("Mensual"))
                .andExpect(jsonPath("$.fechaCreacion", notNullValue()));
    }

    // ---------- ocultar y mostrar ----------

    @Test
    void ocultarYMostrarSonIdempotentes() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));

        accion(ana, ana.presupuestoId, id, "ocultar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculta").value(true));
        accion(ana, ana.presupuestoId, id, "ocultar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculta").value(true));
        accion(ana, ana.presupuestoId, id, "mostrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculta").value(false));
        accion(ana, ana.presupuestoId, id, "mostrar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.oculta").value(false));
    }

    // ---------- mover ----------

    @Test
    void moverDentroDelGrupoRenumeraSinHuecos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "X");
        long a = idDe(crear(ana, ana.presupuestoId, grupo, "A", null));
        crear(ana, ana.presupuestoId, grupo, "B", null);
        long c = idDe(crear(ana, ana.presupuestoId, grupo, "C", null));

        mover(ana, ana.presupuestoId, a, grupo, 2)
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(2));
        esperarGrupo(ana, ana.presupuestoId, 0, "B", "C", "A");

        mover(ana, ana.presupuestoId, c, grupo, 0).andExpect(status().isOk());
        esperarGrupo(ana, ana.presupuestoId, 0, "C", "B", "A");
    }

    @Test
    void moverAOtroGrupoRenumeraOrigenYDestinoSinHuecos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        crear(ana, ana.presupuestoId, x, "A", null);
        long b = idDe(crear(ana, ana.presupuestoId, x, "B", null));
        crear(ana, ana.presupuestoId, x, "C", null);
        crear(ana, ana.presupuestoId, y, "D", null);
        crear(ana, ana.presupuestoId, y, "E", null);

        mover(ana, ana.presupuestoId, b, y, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupoId").value(y))
                .andExpect(jsonPath("$.orden").value(1));

        esperarGrupo(ana, ana.presupuestoId, 0, "A", "C");
        esperarGrupo(ana, ana.presupuestoId, 1, "D", "B", "E");
    }

    @Test
    void moverAlFinalDeOtroGrupoYAUnGrupoVacioFunciona() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        long vacio = crearGrupo(ana, ana.presupuestoId, "Vacio");
        long a = idDe(crear(ana, ana.presupuestoId, x, "A", null));
        long b = idDe(crear(ana, ana.presupuestoId, x, "B", null));
        crear(ana, ana.presupuestoId, y, "D", null);
        crear(ana, ana.presupuestoId, y, "E", null);

        mover(ana, ana.presupuestoId, a, y, 2)
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(2));
        mover(ana, ana.presupuestoId, b, vacio, 0)
                .andExpect(status().isOk()).andExpect(jsonPath("$.orden").value(0));

        esperarGrupo(ana, ana.presupuestoId, 0);
        esperarGrupo(ana, ana.presupuestoId, 1, "D", "E", "A");
        esperarGrupo(ana, ana.presupuestoId, 2, "B");
    }

    @Test
    void moverIncluyeLasCategoriasOcultasEnLaRenumeracion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "X");
        crear(ana, ana.presupuestoId, grupo, "A", null);
        long b = idDe(crear(ana, ana.presupuestoId, grupo, "B", null));
        long c = idDe(crear(ana, ana.presupuestoId, grupo, "C", null));
        accion(ana, ana.presupuestoId, b, "ocultar").andExpect(status().isOk());

        mover(ana, ana.presupuestoId, c, grupo, 0).andExpect(status().isOk());

        esperarGrupo(ana, ana.presupuestoId, 0, "C", "A", "B");
    }

    @Test
    void moverFueraDeRangoDevuelve400ConCodigoDatosInvalidosYNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        long a = idDe(crear(ana, ana.presupuestoId, x, "A", null));
        crear(ana, ana.presupuestoId, x, "B", null);
        crear(ana, ana.presupuestoId, y, "D", null);

        mover(ana, ana.presupuestoId, a, x, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mover(ana, ana.presupuestoId, a, y, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        esperarDatosInvalidos(mover(ana, ana.presupuestoId, a, x, -1), "posicion");

        esperarGrupo(ana, ana.presupuestoId, 0, "A", "B");
        esperarGrupo(ana, ana.presupuestoId, 1, "D");
    }

    @Test
    void moverSinGrupoOSinPosicionDevuelve400ConCodigoDatosInvalidos() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "X");
        long a = idDe(crear(ana, ana.presupuestoId, grupo, "A", null));
        String ruta = ruta(ana.presupuestoId) + "/" + a + "/mover";

        esperarDatosInvalidos(enviar(ana, ruta, Map.of("posicion", 0)), "grupoId");
        esperarDatosInvalidos(enviar(ana, ruta, Map.of("grupoId", grupo)), "posicion");
    }

    @Test
    void moverAUnGrupoDondeElNombreYaExisteDevuelve409YNoCambiaNada() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        long otros = idDe(crear(ana, ana.presupuestoId, x, "Otros", null));
        crear(ana, ana.presupuestoId, y, "otros", null);

        mover(ana, ana.presupuestoId, otros, y, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_YA_EXISTE"));

        esperarGrupo(ana, ana.presupuestoId, 0, "Otros");
        esperarGrupo(ana, ana.presupuestoId, 1, "otros");
    }

    // ---------- árbol ----------

    @Test
    void elArbolDevuelveGruposYCategoriasEnSuOrden() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long x = crearGrupo(ana, ana.presupuestoId, "X");
        long y = crearGrupo(ana, ana.presupuestoId, "Y");
        crear(ana, ana.presupuestoId, x, "A", null);
        crear(ana, ana.presupuestoId, x, "B", null);
        crear(ana, ana.presupuestoId, y, "C", null);
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                                + "/grupos-categorias/" + y + "/mover")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"posicion\": 0}"))
                .andExpect(status().isOk());

        arbol(ana, ana.presupuestoId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Y"))
                .andExpect(jsonPath("$[0].orden").value(0))
                .andExpect(jsonPath("$[0].categorias[0].nombre").value("C"))
                .andExpect(jsonPath("$[1].nombre").value("X"))
                .andExpect(jsonPath("$[1].categorias", hasSize(2)))
                .andExpect(jsonPath("$[1].categorias[0].nombre").value("A"))
                .andExpect(jsonPath("$[1].categorias[1].nombre").value("B"));
    }

    @Test
    void elArbolOmiteLosOcultosPorDefectoYLosIncluyeConIncluirOcultas() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long visible = crearGrupo(ana, ana.presupuestoId, "Visible");
        long oculto = crearGrupo(ana, ana.presupuestoId, "Oculto");
        crear(ana, ana.presupuestoId, visible, "Normal", null);
        long categoriaOculta = idDe(crear(ana, ana.presupuestoId, visible, "Escondida", null));
        crear(ana, ana.presupuestoId, oculto, "Dentro", null);
        accion(ana, ana.presupuestoId, categoriaOculta, "ocultar").andExpect(status().isOk());
        mockMvc.perform(post(RUTA_PRESUPUESTOS + "/" + ana.presupuestoId
                                + "/grupos-categorias/" + oculto + "/ocultar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk());

        mockMvc.perform(get(ruta(ana.presupuestoId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Visible"))
                .andExpect(jsonPath("$[0].categorias", hasSize(1)))
                .andExpect(jsonPath("$[0].categorias[0].nombre").value("Normal"));
        arbol(ana, ana.presupuestoId, false)
                .andExpect(jsonPath("$", hasSize(1)));
        arbol(ana, ana.presupuestoId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].categorias", hasSize(2)))
                .andExpect(jsonPath("$[0].categorias[1].oculta").value(true))
                .andExpect(jsonPath("$[1].oculto").value(true))
                .andExpect(jsonPath("$[1].categorias", hasSize(1)));
    }

    @Test
    void elArbolDeUnPresupuestoSinGruposEsUnaListaVacia() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");

        arbol(ana, ana.presupuestoId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elArbolSoloIncluyeElPresupuestoPedido() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long grupo = crearGrupo(ana, ana.presupuestoId, "Casa");
        crear(ana, ana.presupuestoId, grupo, "Luz", null);
        long grupoViajes = crearGrupo(ana, otro, "Vuelos");
        crear(ana, otro, grupoViajes, "Pasajes", null);

        arbol(ana, ana.presupuestoId, true)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Casa"))
                .andExpect(jsonPath("$[0].categorias", hasSize(1)));
        arbol(ana, otro, true)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Vuelos"));
    }

    // ---------- aislamiento ----------

    @Test
    void elPresupuestoDeOtraPersonaDevuelve404EnCadaOperacion() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));

        esperarNoEncontrado(crear(beto, ana.presupuestoId, grupo, "Hackeo", null));
        esperarNoEncontrado(arbol(beto, ana.presupuestoId, true));
        esperarNoEncontrado(obtener(beto, ana.presupuestoId, id));
        esperarNoEncontrado(editar(beto, ana.presupuestoId, id, cuerpoEdicion("Hackeo", null)));
        esperarNoEncontrado(accion(beto, ana.presupuestoId, id, "ocultar"));
        esperarNoEncontrado(accion(beto, ana.presupuestoId, id, "mostrar"));
        esperarNoEncontrado(mover(beto, ana.presupuestoId, id, grupo, 0));
        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.nombre").value("Luz"));
    }

    @Test
    void laCategoriaDeOtraPersonaPorLaUrlDelPropioPresupuestoDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        Sesion beto = registrar("beto@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));
        long grupoBeto = crearGrupo(beto, beto.presupuestoId, "Mio");

        esperarNoEncontrado(obtener(beto, beto.presupuestoId, id));
        esperarNoEncontrado(editar(beto, beto.presupuestoId, id, cuerpoEdicion("Hackeo", null)));
        esperarNoEncontrado(accion(beto, beto.presupuestoId, id, "ocultar"));
        esperarNoEncontrado(accion(beto, beto.presupuestoId, id, "mostrar"));
        esperarNoEncontrado(mover(beto, beto.presupuestoId, id, grupoBeto, 0));
        esperarNoEncontrado(crear(beto, beto.presupuestoId, grupo, "Hackeo", null));
    }

    @Test
    void laCategoriaDeOtroPresupuestoDelMismoUsuarioDevuelve404EnCadaOperacion()
            throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));
        long grupoOtro = crearGrupo(ana, otro, "Vuelos");

        esperarNoEncontrado(obtener(ana, otro, id));
        esperarNoEncontrado(editar(ana, otro, id, cuerpoEdicion("Otra", null)));
        esperarNoEncontrado(accion(ana, otro, id, "ocultar"));
        esperarNoEncontrado(accion(ana, otro, id, "mostrar"));
        esperarNoEncontrado(mover(ana, otro, id, grupoOtro, 0));
    }

    @Test
    void crearEnUnGrupoDeOtroPresupuestoDelMismoUsuarioDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        esperarNoEncontrado(crear(ana, otro, grupo, "Luz", null));
    }

    @Test
    void moverAUnGrupoDeOtroPresupuestoDelMismoUsuarioDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long otro = idDe(crearPresupuesto(ana, "Viajes"));
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));
        long grupoOtro = crearGrupo(ana, otro, "Vuelos");

        esperarNoEncontrado(mover(ana, ana.presupuestoId, id, grupoOtro, 0));
        obtener(ana, ana.presupuestoId, id).andExpect(jsonPath("$.grupoId").value(grupo));
    }

    @Test
    void unPresupuestoGrupoOCategoriaInexistenteDevuelve404() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");

        esperarNoEncontrado(arbol(ana, 999_999L, false));
        esperarNoEncontrado(crear(ana, ana.presupuestoId, 999_999L, "Luz", null));
        esperarNoEncontrado(obtener(ana, ana.presupuestoId, 999_999L));
        esperarNoEncontrado(mover(ana, ana.presupuestoId, 999_999L, grupo, 0));
    }

    // ---------- sin borrado ----------

    @Test
    void deleteDevuelve405() throws Exception {
        Sesion ana = registrar("ana@ejemplo.com");
        long grupo = crearGrupo(ana, ana.presupuestoId, "Vivienda");
        long id = idDe(crear(ana, ana.presupuestoId, grupo, "Luz", null));

        mockMvc.perform(delete(ruta(ana.presupuestoId) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ana.token)))
                .andExpect(status().isMethodNotAllowed());
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
        // Toda persona recibe "Mi presupuesto" al registrarse: es el único de la lista.
        return new Sesion(token, objectMapper.readTree(presupuestos).get(0).get("id").asLong());
    }

    private ResultActions crearPresupuesto(Sesion sesion, String nombre) throws Exception {
        return enviar(sesion, RUTA_PRESUPUESTOS, Map.of("nombre", nombre));
    }

    private long crearGrupo(Sesion sesion, long presupuestoId, String nombre) throws Exception {
        return idDe(enviar(sesion,
                RUTA_PRESUPUESTOS + "/" + presupuestoId + "/grupos-categorias",
                Map.of("nombre", nombre)));
    }

    private ResultActions crear(
            Sesion sesion, long presupuestoId, long grupoId, String nombre, String nota)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("grupoId", grupoId);
        cuerpo.put("nombre", nombre);
        if (nota != null) {
            cuerpo.put("nota", nota);
        }
        return enviar(sesion, ruta(presupuestoId), cuerpo);
    }

    private ResultActions arbol(Sesion sesion, long presupuestoId, boolean incluirOcultas)
            throws Exception {
        return mockMvc.perform(get(ruta(presupuestoId))
                .param("incluirOcultas", String.valueOf(incluirOcultas))
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

    private ResultActions mover(
            Sesion sesion, long presupuestoId, long id, long grupoId, int posicion)
            throws Exception {
        return enviar(sesion, ruta(presupuestoId) + "/" + id + "/mover",
                Map.of("grupoId", grupoId, "posicion", posicion));
    }

    private ResultActions enviar(Sesion sesion, String ruta, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(post(ruta)
                .header(HttpHeaders.AUTHORIZATION, bearer(sesion.token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private static Map<String, Object> cuerpoEdicion(String nombre, String nota) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        if (nota != null) {
            cuerpo.put("nota", nota);
        }
        return cuerpo;
    }

    /** Comprueba nombres y orden consecutivo de las categorías del grupo en esa posición. */
    private void esperarGrupo(Sesion sesion, long presupuestoId, int posicionGrupo,
            String... nombres) throws Exception {
        String cuerpo = arbol(sesion, presupuestoId, true)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode categorias = objectMapper.readTree(cuerpo).get(posicionGrupo).get("categorias");
        assertThat(categorias.size()).isEqualTo(nombres.length);
        for (int i = 0; i < nombres.length; i++) {
            assertThat(categorias.get(i).get("nombre").asString()).isEqualTo(nombres[i]);
            assertThat(categorias.get(i).get("orden").asInt()).isEqualTo(i);
        }
    }

    private long idDe(ResultActions creacion) throws Exception {
        String cuerpo = creacion.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("id").asLong();
    }

    private static String ruta(long presupuestoId) {
        return RUTA_PRESUPUESTOS + "/" + presupuestoId + "/categorias";
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
