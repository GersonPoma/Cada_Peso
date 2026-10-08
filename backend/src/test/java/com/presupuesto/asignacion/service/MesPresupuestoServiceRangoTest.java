package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.asignacion.entity.AsignacionMensual;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code calcularFilas} contra {@code calcular} mes a mes con la base de datos real, en un
 * historial de 8 meses y un rango que empieza a mitad (del mes 4 al 8): sobregasto que no se
 * arrastra, saldo positivo que sí, tarjeta con categoría de pago, división, categorías sin
 * movimientos en varios meses y una categoría cuyo primer dato llega tarde.
 */
@SpringBootTest
@Transactional
class MesPresupuestoServiceRangoTest {

    private static final YearMonth ENERO = YearMonth.of(2026, 1);

    @Autowired
    private MesPresupuestoService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private AsignacionMensualRepository asignacionRepository;

    private Presupuesto presupuesto;
    private Categoria comida;
    private Categoria ocio;
    private Categoria hogar;
    private Categoria pagoVisa;
    private Categoria tardia;

    @BeforeEach
    void historialDeOchoMeses() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-rango-filas@ejemplo.com").contrasena("hash").build());
        presupuesto = presupuestoRepository.saveAndFlush(Presupuesto.builder()
                .usuario(ana).nombre("Casa").nombreNormalizado("casa").moneda("BOB").build());
        Cuenta banco = cuenta("Banco", TipoCuenta.CORRIENTE, true);
        Cuenta visa = cuenta("Visa", TipoCuenta.TARJETA_CREDITO, true);
        Cuenta fuera = cuenta("Inversion", TipoCuenta.INVERSION, false);
        GrupoCategoria vida = grupo("Vida diaria", TipoGrupoCategoria.NORMAL);
        GrupoCategoria pagos = grupo("Pagos de tarjetas", TipoGrupoCategoria.PAGOS_TARJETA);
        comida = categoria(vida, "Comida", null);
        ocio = categoria(vida, "Ocio", null);
        hogar = categoria(vida, "Hogar", null);
        tardia = categoria(vida, "Tardia", null);
        pagoVisa = categoria(pagos, "Pago: Visa", visa);

        asignar(comida, 1, 100_000L);
        asignar(comida, 2, 50_000L);
        asignar(comida, 5, 200_000L);
        asignar(comida, 6, 30_000L);
        asignar(ocio, 2, 20_000L);
        asignar(hogar, 4, 40_000L);
        asignar(pagoVisa, 3, 30_000L);
        asignar(tardia, 7, 5_000L);

        guardar(banco, 1, 2, 500_000L, null);
        guardar(banco, 1, 5, -20_000L, comida);
        guardar(banco, 2, 5, -90_000L, comida);
        guardar(visa, 3, 6, -30_000L, comida);
        guardar(banco, 3, 7, -50_000L, comida);
        Transaccion dividida = guardar(banco, 4, 8, -60_000L, null);
        dividida.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-10_000L).categoria(comida).build(),
                SubTransaccion.builder().monto(-50_000L).categoria(hogar).build()));
        guardar(banco, 4, 9, -5_000L, ocio);
        guardar(banco, 4, 10, -3_000L, null);
        guardar(fuera, 4, 11, -9_000L, null);
        transferir(banco, visa, 5, 12, 30_000L);
        guardar(banco, 6, 3, -7_000L, comida);
        guardar(banco, 7, 4, -1_000L, tardia);
        transaccionRepository.flush();
    }

    private Map<Long, FilaMes> delMes(YearMonth mes) {
        return service.calcular(presupuesto.getId(), mes).filas();
    }

    @Test
    void cadaMesDeUnRangoQueEmpiezaAMitadDelHistorialIgualaALlamarACalcular() {
        YearMonth desde = YearMonth.of(2026, 4);
        YearMonth hasta = YearMonth.of(2026, 8);

        Map<YearMonth, Map<Long, FilaMes>> filas =
                service.calcularFilas(presupuesto.getId(), desde, hasta);

        assertThat(filas.keySet()).containsExactly(
                YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6),
                YearMonth.of(2026, 7), YearMonth.of(2026, 8));
        for (YearMonth mes : filas.keySet()) {
            assertThat(filas.get(mes)).as("filas de %s", mes).isEqualTo(delMes(mes));
        }
    }

    @Test
    void elPrimerMesDelRangoTraeElArrastreDeLosMesesAnterioresADesde() {
        Map<YearMonth, Map<Long, FilaMes>> filas = service.calcularFilas(
                presupuesto.getId(), YearMonth.of(2026, 4), YearMonth.of(2026, 5));
        Map<Long, FilaMes> abril = filas.get(YearMonth.of(2026, 4));
        Map<Long, FilaMes> mayo = filas.get(YearMonth.of(2026, 5));

        // ocio: 20000 asignados en febrero llegan a abril y se gastan 5000
        assertThat(abril.get(ocio.getId())).isEqualTo(new FilaMes(0L, -5_000L, 15_000L));
        // comida: el sobregasto de marzo (40000 - 80000) no se arrastra; abril gasta 10000
        assertThat(abril.get(comida.getId())).isEqualTo(new FilaMes(0L, -10_000L, -10_000L));
        // pago de la tarjeta: 30000 de reserva + 30000 asignados en marzo pasan a abril
        assertThat(abril.get(pagoVisa.getId())).isEqualTo(new FilaMes(0L, 0L, 60_000L));
        // y el pago de 30000 de mayo baja la reserva
        assertThat(mayo.get(pagoVisa.getId())).isEqualTo(new FilaMes(0L, -30_000L, 30_000L));
    }

    @Test
    void unaCategoriaConPrimerDatoTardioApareceSoloDesdeEseMes() {
        Map<YearMonth, Map<Long, FilaMes>> filas = service.calcularFilas(
                presupuesto.getId(), YearMonth.of(2026, 6), YearMonth.of(2026, 8));

        assertThat(filas.get(YearMonth.of(2026, 6))).doesNotContainKey(tardia.getId());
        assertThat(filas.get(YearMonth.of(2026, 7)).get(tardia.getId()))
                .isEqualTo(new FilaMes(5_000L, -1_000L, 4_000L));
        assertThat(filas.get(YearMonth.of(2026, 8)).get(tardia.getId()))
                .isEqualTo(new FilaMes(0L, 0L, 4_000L));
        for (YearMonth mes : filas.keySet()) {
            assertThat(filas.get(mes)).as("filas de %s", mes).isEqualTo(delMes(mes));
        }
    }

    @Test
    void elRangoCompletoYUnSoloMesTambienCoinciden() {
        Map<YearMonth, Map<Long, FilaMes>> completo = service.calcularFilas(
                presupuesto.getId(), ENERO, YearMonth.of(2026, 8));
        Map<YearMonth, Map<Long, FilaMes>> soloAgosto = service.calcularFilas(
                presupuesto.getId(), YearMonth.of(2026, 8), YearMonth.of(2026, 8));

        assertThat(completo).hasSize(8);
        for (YearMonth mes : completo.keySet()) {
            assertThat(completo.get(mes)).as("filas de %s", mes).isEqualTo(delMes(mes));
        }
        assertThat(soloAgosto.get(YearMonth.of(2026, 8)))
                .isEqualTo(completo.get(YearMonth.of(2026, 8)));
    }

    @Test
    void unRangoAnteriorAlPrimerDatoDevuelveFilasVaciasComoCalcular() {
        Map<YearMonth, Map<Long, FilaMes>> filas = service.calcularFilas(
                presupuesto.getId(), YearMonth.of(2025, 10), YearMonth.of(2025, 12));

        assertThat(filas).hasSize(3);
        for (YearMonth mes : filas.keySet()) {
            assertThat(filas.get(mes)).isEmpty();
            assertThat(filas.get(mes)).isEqualTo(delMes(mes));
        }
    }

    @Test
    void unRangoPosteriorAlUltimoDatoRepiteElArrastre() {
        Map<YearMonth, Map<Long, FilaMes>> filas = service.calcularFilas(
                presupuesto.getId(), YearMonth.of(2026, 11), YearMonth.of(2026, 12));

        for (YearMonth mes : filas.keySet()) {
            assertThat(filas.get(mes)).as("filas de %s", mes).isEqualTo(delMes(mes));
        }
        assertThat(filas.get(YearMonth.of(2026, 12)).get(tardia.getId()))
                .isEqualTo(new FilaMes(0L, 0L, 4_000L));
    }

    // ---------- armado de datos ----------

    private Cuenta cuenta(String nombre, TipoCuenta tipo, boolean enPresupuesto) {
        return cuentaRepository.saveAndFlush(Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(tipo)
                .enPresupuesto(enPresupuesto)
                .saldoInicial(0L)
                .build());
    }

    private GrupoCategoria grupo(String nombre, TipoGrupoCategoria tipo) {
        return grupoRepository.save(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(0)
                .tipo(tipo)
                .build());
    }

    private Categoria categoria(GrupoCategoria grupo, String nombre, Cuenta tarjeta) {
        return categoriaRepository.save(Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(0)
                .cuentaTarjeta(tarjeta)
                .build());
    }

    private void asignar(Categoria categoria, int mes, long asignado) {
        asignacionRepository.save(AsignacionMensual.builder()
                .categoria(categoria)
                .mes(LocalDate.of(2026, mes, 1))
                .asignado(asignado)
                .build());
    }

    private Transaccion guardar(Cuenta cuenta, int mes, int dia, long monto, Categoria categoria) {
        return transaccionRepository.saveAndFlush(Transaccion.builder()
                .cuenta(cuenta)
                .fecha(LocalDate.of(2026, mes, dia))
                .monto(monto)
                .categoria(categoria)
                .build());
    }

    private void transferir(Cuenta origen, Cuenta destino, int mes, int dia, long monto) {
        Transaccion salida = Transaccion.builder()
                .cuenta(origen).fecha(LocalDate.of(2026, mes, dia)).monto(-monto).build();
        Transaccion entrada = Transaccion.builder()
                .cuenta(destino).fecha(LocalDate.of(2026, mes, dia)).monto(monto).build();
        transaccionRepository.saveAllAndFlush(List.of(salida, entrada));
        salida.enlazarCon(entrada);
        entrada.enlazarCon(salida);
        transaccionRepository.saveAllAndFlush(List.of(salida, entrada));
    }
}
