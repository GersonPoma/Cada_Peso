package com.presupuesto.asignacion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ActividadMensualRepositoryTest {

    private static final LocalDate FIN_ANIO = LocalDate.of(2026, 12, 31);

    @Autowired
    private ActividadMensualRepository repository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Presupuesto casa;
    private Presupuesto viajes;
    private Cuenta banco;
    private Cuenta fuera;
    private Cuenta cerrada;
    private Cuenta cuentaViajes;
    private Categoria comida;
    private Categoria ocio;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-actividad@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        banco = cuentaRepository.save(cuenta(casa, "Banco", TipoCuenta.CORRIENTE, true, 0L));
        fuera = cuentaRepository.save(cuenta(casa, "Fuera", TipoCuenta.INVERSION, false, 0L));
        cerrada = cuentaRepository.save(cuenta(casa, "Cerrada", TipoCuenta.EFECTIVO, true, 0L));
        cerrada.cerrar();
        cuentaViajes = cuentaRepository.saveAndFlush(
                cuenta(viajes, "Banco", TipoCuenta.CORRIENTE, true, 0L));
        comida = categoria(casa, "Comida");
        ocio = categoria(casa, "Ocio");
    }

    @Test
    void actividadSimpleAgrupaPorCategoriaYMesCalendario() {
        guardar(banco, "2026-01-05", -12_000L, comida);
        guardar(banco, "2026-01-31", -8_000L, comida);
        guardar(banco, "2026-02-01", -5_000L, comida);
        guardar(banco, "2026-01-10", -1_000L, ocio);

        List<ActividadPorMes> filas = repository.actividadSimple(casa.getId(), FIN_ANIO);

        assertThat(total(filas, comida, 2026, 1)).isEqualTo(-20_000L);
        assertThat(total(filas, comida, 2026, 2)).isEqualTo(-5_000L);
        assertThat(total(filas, ocio, 2026, 1)).isEqualTo(-1_000L);
        assertThat(filas).hasSize(3);
    }

    @Test
    void actividadSimpleIgnoraCuentasFueraDelPresupuestoYCuentaLasCerradas() {
        guardar(banco, "2026-01-05", -1_000L, comida);
        guardar(fuera, "2026-01-05", -50_000L, comida);
        guardar(cerrada, "2026-01-05", -7_000L, comida);

        List<ActividadPorMes> filas = repository.actividadSimple(casa.getId(), FIN_ANIO);

        assertThat(total(filas, comida, 2026, 1)).isEqualTo(-8_000L);
    }

    @Test
    void actividadSimpleRespetaElTopeDeFechaYNoMezclaPresupuestos() {
        guardar(banco, "2026-01-05", -1_000L, comida);
        guardar(banco, "2026-03-05", -2_000L, comida);
        Categoria hotel = categoria(viajes, "Hotel");
        guardar(cuentaViajes, "2026-01-05", -9_999L, hotel);
        guardar(banco, "2026-01-06", -4_000L, null);

        List<ActividadPorMes> filas =
                repository.actividadSimple(casa.getId(), LocalDate.of(2026, 2, 28));

        assertThat(filas).hasSize(1);
        assertThat(total(filas, comida, 2026, 1)).isEqualTo(-1_000L);
    }

    @Test
    void actividadDivididaSumaLasPartesPorCategoriaYMes() {
        Transaccion dividida = guardar(banco, "2026-01-10", -30_000L, null);
        dividida.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-10_000L).categoria(comida).build(),
                SubTransaccion.builder().monto(-20_000L).categoria(ocio).build()));
        Transaccion otra = guardar(banco, "2026-01-20", -5_000L, null);
        otra.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-2_000L).categoria(comida).build(),
                SubTransaccion.builder().monto(-3_000L).build()));
        Transaccion enFuera = guardar(fuera, "2026-01-10", -4_000L, null);
        enFuera.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(-1_000L).categoria(comida).build(),
                SubTransaccion.builder().monto(-3_000L).categoria(ocio).build()));
        transaccionRepository.flush();

        List<ActividadPorMes> filas = repository.actividadDividida(casa.getId(), FIN_ANIO);

        assertThat(total(filas, comida, 2026, 1)).isEqualTo(-12_000L);
        assertThat(total(filas, ocio, 2026, 1)).isEqualTo(-20_000L);
        assertThat(filas).hasSize(2);
    }

    @Test
    void ingresosSoloSumanEntradasSinCategoriaNiSubtransaccionesDeCuentasDelPresupuesto() {
        guardar(banco, "2026-01-05", 200_000L, null);
        guardar(banco, "2026-02-10", 80_000L, null);
        guardar(banco, "2026-01-06", -30_000L, null);
        guardar(banco, "2026-01-07", 9_000L, comida);
        guardar(fuera, "2026-01-08", 70_000L, null);
        guardar(cerrada, "2026-01-09", 1_000L, null);
        Transaccion dividida = guardar(banco, "2026-01-10", 6_000L, null);
        dividida.reemplazarSubtransacciones(List.of(
                SubTransaccion.builder().monto(4_000L).categoria(comida).build(),
                SubTransaccion.builder().monto(2_000L).build()));
        transaccionRepository.flush();

        assertThat(repository.ingresosSinCategoria(casa.getId(), LocalDate.of(2026, 1, 31)))
                .isEqualTo(201_000L);
        assertThat(repository.ingresosSinCategoria(casa.getId(), FIN_ANIO))
                .isEqualTo(281_000L);
        assertThat(repository.ingresosSinCategoria(viajes.getId(), FIN_ANIO)).isZero();
    }

    @Test
    void saldosInicialesSoloSumanPositivosDeCuentasDelPresupuestoQueNoSonTarjeta() {
        cuentaRepository.save(cuenta(casa, "Ahorro", TipoCuenta.AHORRO, true, 100_000L));
        cuentaRepository.save(cuenta(casa, "Tarjeta", TipoCuenta.TARJETA_CREDITO, true, 50_000L));
        cuentaRepository.save(cuenta(casa, "Seguimiento", TipoCuenta.INVERSION, false, 70_000L));
        cuentaRepository.save(cuenta(casa, "Deuda", TipoCuenta.PRESTAMO, true, -20_000L));
        cuentaRepository.save(cuenta(casa, "Vieja", TipoCuenta.EFECTIVO, true, 5_000L));
        cuentaRepository.saveAndFlush(cuenta(viajes, "Otra", TipoCuenta.AHORRO, true, 999L));

        assertThat(repository.saldosInicialesPositivos(casa.getId())).isEqualTo(105_000L);
        assertThat(repository.saldosInicialesPositivos(viajes.getId())).isEqualTo(999L);
    }

    @Test
    void sinDatosLasSumasSonCero() {
        assertThat(repository.ingresosSinCategoria(casa.getId(), FIN_ANIO)).isZero();
        assertThat(repository.saldosInicialesPositivos(casa.getId())).isZero();
        assertThat(repository.actividadSimple(casa.getId(), FIN_ANIO)).isEmpty();
        assertThat(repository.actividadDividida(casa.getId(), FIN_ANIO)).isEmpty();
    }

    private static long total(
            List<ActividadPorMes> filas, Categoria categoria, int anio, int mes) {
        return filas.stream()
                .filter(f -> f.getCategoriaId().equals(categoria.getId())
                        && f.getAnio() == anio && f.getMes() == mes)
                .mapToLong(ActividadPorMes::getTotal)
                .sum();
    }

    private Transaccion guardar(Cuenta cuenta, String fecha, long monto, Categoria categoria) {
        return transaccionRepository.saveAndFlush(Transaccion.builder()
                .cuenta(cuenta)
                .fecha(LocalDate.parse(fecha))
                .monto(monto)
                .categoria(categoria)
                .build());
    }

    private Categoria categoria(Presupuesto presupuesto, String nombre) {
        GrupoCategoria grupo = grupoRepository.save(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre("Grupo " + nombre)
                .nombreNormalizado(GrupoCategoria.normalizar("Grupo " + nombre))
                .orden(0)
                .build());
        return categoriaRepository.save(Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(0)
                .build());
    }

    private static Cuenta cuenta(
            Presupuesto presupuesto,
            String nombre,
            TipoCuenta tipo,
            boolean enPresupuesto,
            long saldoInicial) {
        return Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(tipo)
                .enPresupuesto(enPresupuesto)
                .saldoInicial(saldoInicial)
                .build();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }
}
