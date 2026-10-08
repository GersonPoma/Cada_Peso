package com.presupuesto.importacion.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas contra un servidor real: el límite multipart lo aplica Tomcat, que MockMvc no emula.
 * Como confirman sus datos (no hay transacción de prueba que los revierta), corren contra una
 * base propia, {@code presupuesto_test} (con el mismo servidor y credenciales que la normal), y
 * al terminar cada test vacían lo que crearon. Antes de borrar comprueban que la base
 * conectada sea esa, para no tocar jamás la de desarrollo.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:postgresql://"
                + "${DB_HOST:localhost}:${DB_PORT:5432}/presupuesto_test")
class ImportacionTransporteTest {

    private static final String LIMITE = "multipart/form-data; boundary=";
    private static final String FRONTERA = "----frontera-de-prueba";

    @Value("${local.server.port}")
    private int puerto;

    @Value("${spring.servlet.multipart.file-size-threshold}")
    private DataSize umbralEnMemoria;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize techoMultipart;

    @Value("${importacion.max-bytes}")
    private long maxBytes;

    private final HttpClient cliente = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private long presupuestoId;
    private long cuentaId;

    @BeforeEach
    void crearDatos() throws Exception {
        String registro = objectMapper.writeValueAsString(Map.of(
                "email", "transporte-" + UUID.randomUUID() + "@ejemplo.com",
                "contrasena", "secreta123",
                "nombre", "Ana",
                "apellido", "Rojas",
                "fechaNacimiento", "1990-05-20"));
        HttpResponse<String> alta = json("POST", "/api/v1/auth/registro", registro, null);
        assertThat(alta.statusCode()).isEqualTo(201);
        token = objectMapper.readTree(alta.body()).get("token").asString();
        JsonNode presupuestos = objectMapper.readTree(
                json("GET", "/api/v1/presupuestos", null, token).body());
        presupuestoId = presupuestos.get(0).get("id").asLong();
        HttpResponse<String> cuenta = json("POST",
                "/api/v1/presupuestos/" + presupuestoId + "/cuentas",
                objectMapper.writeValueAsString(Map.of(
                        "nombre", "Banco", "tipo", "CORRIENTE",
                        "enPresupuesto", true, "saldoInicial", 0)),
                token);
        assertThat(cuenta.statusCode()).isEqualTo(201);
        cuentaId = objectMapper.readTree(cuenta.body()).get("id").asLong();
    }

    /** Vacía la base de pruebas: el usuario creado y todo lo que cuelga de él. */
    @AfterEach
    void borrarLoCreado() {
        String base = jdbc.queryForObject("select current_database()", String.class);
        assertThat(base).isEqualTo("presupuesto_test");
        jdbc.execute("truncate table usuarios cascade");
    }

    @Test
    void unArchivoMayorQueElTechoMultipartDa400NoUn500EnAmbosEndpoints() throws Exception {
        // Poco más del techo (4 MB): lo que sobra cabe en lo que Tomcat descarta (2 MB) tras
        // responder, así el cliente siempre alcanza a leer el 400 y la prueba no es intermitente.
        byte[] enorme = new byte[4 * 1024 * 1024 + 512 * 1024];
        java.util.Arrays.fill(enorme, (byte) 'a');

        for (String sufijo : new String[] {"/vista-previa", ""}) {
            HttpResponse<String> respuesta = subir(sufijo, enorme);

            assertThat(respuesta.statusCode()).isEqualTo(400);
            JsonNode problema = objectMapper.readTree(respuesta.body());
            assertThat(problema.get("codigo").asString()).isEqualTo("DATOS_INVALIDOS");
            assertThat(problema.get("detail").asString())
                    .isEqualTo("El archivo supera el tamaño permitido");
        }
    }

    @Test
    void unArchivoEntre2Y4MbDa400ConElMensajeDelLimiteDeLaAplicacion() throws Exception {
        byte[] tresMb = new byte[3 * 1024 * 1024];
        java.util.Arrays.fill(tresMb, (byte) 'a');

        for (String sufijo : new String[] {"/vista-previa", ""}) {
            HttpResponse<String> respuesta = subir(sufijo, tresMb);

            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(objectMapper.readTree(respuesta.body()).get("detail").asString())
                    .contains("2 MB");
        }
    }

    /**
     * Tomcat borra sus temporales al terminar la petición, así que mirar el disco después no
     * prueba nada: lo que garantiza que las partes admitidas se queden en memoria es que el
     * umbral multipart cubra todo lo que la aplicación acepta.
     */
    @Test
    void elUmbralEnMemoriaCubreTodoLoQueLaAplicacionAcepta() {
        assertThat(umbralEnMemoria.toBytes()).isGreaterThanOrEqualTo(maxBytes);
        assertThat(umbralEnMemoria.toBytes()).isGreaterThanOrEqualTo(techoMultipart.toBytes());
        assertThat(techoMultipart.toBytes()).isGreaterThan(maxBytes);
    }

    @Test
    void elArchivoNoDejaTemporalesDeSubidaEnElServidor() throws Exception {
        StringBuilder csv = new StringBuilder("Fecha;Descripcion;Monto;Memo\n");
        for (int i = 0; i < 20_000; i++) {
            csv.append("05/09/2026;Comercio ").append(i).append(";-").append(i + 1)
                    .append(",00;").append("x".repeat(20)).append('\n');
        }
        byte[] contenido = csv.toString().getBytes(StandardCharsets.UTF_8);
        assertThat(contenido.length).isGreaterThan(512 * 1024).isLessThan(2 * 1024 * 1024);
        List<Path> antes = archivosDeSubida();

        HttpResponse<String> previa = subir("/vista-previa", contenido);
        HttpResponse<String> error = subir("", new byte[] {(byte) 0xE9});

        assertThat(previa.statusCode()).isEqualTo(400);
        assertThat(error.statusCode()).isEqualTo(400);
        assertThat(archivosDeSubida()).containsExactlyInAnyOrderElementsOf(antes);
    }

    @Test
    void unaImportacionRealPorHttpCreaLasFilas() throws Exception {
        byte[] contenido =
                "Fecha;Descripcion;Monto;Memo\n05/09/2026;Cafe;-4,50;\n"
                        .getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> respuesta = subir("", contenido);

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(respuesta.body()).get("importadas").asLong())
                .isEqualTo(1);
        assertThat(archivosDeSubida()).isEmpty();
    }

    /** Archivos temporales de subida que Tomcat deja en su directorio de trabajo. */
    private static List<Path> archivosDeSubida() throws IOException {
        Path temporal = Path.of(System.getProperty("java.io.tmpdir"));
        List<Path> encontrados = new ArrayList<>();
        try (Stream<Path> directorios = Files.list(temporal)) {
            for (Path directorio : directorios
                    .filter(p -> p.getFileName().toString().startsWith("tomcat."))
                    .toList()) {
                try (Stream<Path> recorrido = Files.walk(directorio)) {
                    recorrido.filter(p -> Files.isRegularFile(p)
                                    && p.getFileName().toString().startsWith("upload_"))
                            .forEach(encontrados::add);
                }
            }
        }
        return encontrados;
    }

    private HttpResponse<String> subir(String sufijo, byte[] contenido) throws Exception {
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        Map<String, String> campos = Map.of(
                "separador", "PUNTO_Y_COMA",
                "tieneEncabezado", "true",
                "columnaFecha", "0",
                "formatoFecha", "dd/MM/yyyy",
                "columnaMonto", "2",
                "separadorDecimal", "COMA",
                "columnaDescripcion", "1");
        for (Map.Entry<String, String> campo : campos.entrySet()) {
            cuerpo.writeBytes(("--" + FRONTERA + "\r\nContent-Disposition: form-data; name=\""
                    + campo.getKey() + "\"\r\n\r\n" + campo.getValue() + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        cuerpo.writeBytes(("--" + FRONTERA + "\r\nContent-Disposition: form-data; "
                + "name=\"archivo\"; filename=\"extracto.csv\"\r\nContent-Type: text/csv\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(contenido);
        cuerpo.writeBytes(("\r\n--" + FRONTERA + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/v1/presupuestos/" + presupuestoId + "/cuentas/" + cuentaId
                        + "/importacion" + sufijo))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", LIMITE + FRONTERA)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
                .build();
        return enviar(peticion);
    }

    private HttpResponse<String> json(String metodo, String ruta, String cuerpo, String bearer)
            throws Exception {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json");
        if (bearer != null) {
            constructor.header("Authorization", "Bearer " + bearer);
        }
        constructor.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return enviar(constructor.build());
    }

    private HttpResponse<String> enviar(HttpRequest peticion) throws Exception {
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
