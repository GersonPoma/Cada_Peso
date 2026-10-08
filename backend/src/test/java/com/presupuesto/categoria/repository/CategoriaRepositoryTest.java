package com.presupuesto.categoria.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository.PagoDeTarjeta;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;
    private GrupoCategoria vivienda;
    private GrupoCategoria comida;
    private GrupoCategoria ajeno;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-categorias@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        vivienda = grupoRepository.saveAndFlush(nuevoGrupo(casa, "Vivienda", 0));
        comida = grupoRepository.saveAndFlush(nuevoGrupo(casa, "Comida", 1));
        ajeno = grupoRepository.saveAndFlush(nuevoGrupo(viajes, "Vuelos", 0));
    }

    @Test
    void findByIdAndGrupoPresupuestoIdSoloEncuentraLaCategoriaDeEsePresupuesto() {
        Categoria alquiler = categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                alquiler.getId(), casa.getId())).isPresent();
        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                alquiler.getId(), viajes.getId())).isEmpty();
        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                Long.MAX_VALUE, casa.getId())).isEmpty();
    }

    @Test
    void laListaDeUnGrupoEstaOrdenadaPorOrdenYEsSoloDeEseGrupo() {
        categoriaRepository.save(nueva(vivienda, "Segunda", 1));
        categoriaRepository.save(nueva(vivienda, "Primera", 0));
        categoriaRepository.saveAndFlush(nueva(comida, "Otra", 0));

        assertThat(categoriaRepository.findByGrupoIdOrderByOrden(vivienda.getId()))
                .extracting(Categoria::getNombre)
                .containsExactly("Primera", "Segunda");
    }

    @Test
    void laListaDelPresupuestoEsSoloDeEsePresupuestoYFiltraLasOcultas() {
        categoriaRepository.save(nueva(vivienda, "Alquiler", 0));
        categoriaRepository.save(nueva(comida, "Mercado", 0));
        Categoria oculta = nueva(vivienda, "Antigua", 1);
        oculta.ocultar();
        categoriaRepository.save(oculta);
        categoriaRepository.saveAndFlush(nueva(ajeno, "Pasajes", 0));

        assertThat(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactlyInAnyOrder("Alquiler", "Mercado", "Antigua");
        assertThat(categoriaRepository
                .findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactlyInAnyOrder("Alquiler", "Mercado");
    }

    @Test
    void countCuentaSoloLasCategoriasDelGrupo() {
        categoriaRepository.save(nueva(vivienda, "Uno", 0));
        categoriaRepository.save(nueva(vivienda, "Dos", 1));
        categoriaRepository.saveAndFlush(nueva(comida, "Tres", 0));

        assertThat(categoriaRepository.countByGrupoId(vivienda.getId())).isEqualTo(2);
        assertThat(categoriaRepository.countByGrupoId(comida.getId())).isEqualTo(1);
    }

    @Test
    void existsPorNombreNormalizadoRespetaAlGrupoYExcluyeElPropioId() {
        Categoria alquiler = categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizado(
                vivienda.getId(), "alquiler")).isTrue();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizado(
                comida.getId(), "alquiler")).isFalse();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                vivienda.getId(), "alquiler", alquiler.getId())).isFalse();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                vivienda.getId(), "alquiler", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void laRestriccionUnicaRechazaElMismoNombreNormalizadoDelMismoGrupo() {
        categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThrows(DataIntegrityViolationException.class,
                () -> categoriaRepository.saveAndFlush(nueva(vivienda, "ALQUILER", 1)));
    }

    @Test
    void laRestriccionUnicaPermiteElMismoNombreEnOtroGrupo() {
        categoriaRepository.saveAndFlush(nueva(vivienda, "Otros", 0));

        Categoria enComida = categoriaRepository.saveAndFlush(nueva(comida, "Otros", 0));

        assertThat(enComida.getId()).isNotNull();
    }

    @Test
    void laRestriccionUnicaImpideDosCategoriasDePagoParaLaMismaTarjeta() {
        Cuenta visa = tarjeta(casa, "Visa", true);
        categoriaRepository.saveAndFlush(pago(vivienda, "Pago: Visa", 0, visa));

        assertThrows(DataIntegrityViolationException.class,
                () -> categoriaRepository.saveAndFlush(pago(comida, "Pago: Visa", 0, visa)));
    }

    @Test
    void laCategoriaDePagoSeEncuentraPorSuTarjeta() {
        Cuenta visa = tarjeta(casa, "Visa", true);
        Categoria pago = categoriaRepository.saveAndFlush(pago(vivienda, "Pago: Visa", 0, visa));

        assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId()))
                .get().extracting(Categoria::getId).isEqualTo(pago.getId());
        assertThat(categoriaRepository.findByCuentaTarjetaId(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void pagosDeTarjetasDevuelveSoloLasDelPresupuesto() {
        Cuenta visa = tarjeta(casa, "Visa", true);
        Cuenta master = tarjeta(viajes, "Master", true);
        Categoria pagoVisa =
                categoriaRepository.save(pago(vivienda, "Pago: Visa", 0, visa));
        categoriaRepository.save(pago(ajeno, "Pago: Master", 0, master));
        categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 1));

        List<PagoDeTarjeta> pagos = categoriaRepository.pagosDeTarjetas(casa.getId());

        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).getCuentaId()).isEqualTo(visa.getId());
        assertThat(pagos.get(0).getCategoriaId()).isEqualTo(pagoVisa.getId());
        assertThat(categoriaRepository.pagosDeTarjetas(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void lasTarjetasSinCategoriaSonSoloLasDelPresupuestoQueNoLaTienen() {
        Cuenta conCategoria = tarjeta(casa, "Visa", true);
        Cuenta sinCategoria = tarjeta(casa, "Master", true);
        tarjeta(casa, "Seguimiento", false);
        cuentaRepository.saveAndFlush(Cuenta.builder().presupuesto(casa).nombre("Banco")
                .nombreNormalizado("banco").tipo(TipoCuenta.CORRIENTE).build());
        Cuenta deViajes = tarjeta(viajes, "Visa", true);
        categoriaRepository.saveAndFlush(pago(vivienda, "Pago: Visa", 0, conCategoria));

        assertThat(categoriaRepository.tarjetasSinCategoria(casa.getId()))
                .extracting(Cuenta::getId).containsExactly(sinCategoria.getId());
        assertThat(categoriaRepository.tarjetasSinCategoria(viajes.getId()))
                .extracting(Cuenta::getId).containsExactly(deViajes.getId());
        assertThat(categoriaRepository.presupuestosConTarjetasSinCategoria())
                .contains(casa.getId(), viajes.getId());
    }

    @Test
    void sinTarjetasPendientesElPresupuestoNoApareceEnLaMigracion() {
        Cuenta visa = tarjeta(casa, "Visa", true);
        categoriaRepository.saveAndFlush(pago(vivienda, "Pago: Visa", 0, visa));

        assertThat(categoriaRepository.presupuestosConTarjetasSinCategoria())
                .doesNotContain(casa.getId());
        assertThat(categoriaRepository.tarjetasSinCategoria(casa.getId())).isEmpty();
    }

    private Cuenta tarjeta(Presupuesto presupuesto, String nombre, boolean enPresupuesto) {
        return cuentaRepository.saveAndFlush(Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(TipoCuenta.TARJETA_CREDITO)
                .enPresupuesto(enPresupuesto)
                .build());
    }

    private static Categoria pago(GrupoCategoria grupo, String nombre, int orden, Cuenta tarjeta) {
        return Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(orden)
                .cuentaTarjeta(tarjeta)
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

    private static GrupoCategoria nuevoGrupo(Presupuesto presupuesto, String nombre, int orden) {
        return GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build();
    }

    private static Categoria nueva(GrupoCategoria grupo, String nombre, int orden) {
        return Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(orden)
                .build();
    }

    @Test
    void lasConsultasEnOrdenDelArbolOrdenanPorGrupoYLuegoPorCategoria() {
        Categoria mercado = categoriaRepository.save(nueva(comida, "Mercado", 0));
        Categoria alquilerOculta = nueva(vivienda, "Alquiler", 1);
        alquilerOculta.ocultar();
        categoriaRepository.save(alquilerOculta);
        Categoria luz = categoriaRepository.save(nueva(vivienda, "Luz", 0));
        categoriaRepository.saveAndFlush(nueva(ajeno, "Vuelo", 0));

        assertThat(categoriaRepository
                .findByGrupoPresupuestoIdOrderByGrupoOrdenAscOrdenAsc(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactly("Luz", "Alquiler", "Mercado");
        assertThat(categoriaRepository
                .findByGrupoPresupuestoIdAndOcultaFalseOrderByGrupoOrdenAscOrdenAsc(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactly("Luz", "Mercado");
        assertThat(categoriaRepository
                .findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc(
                        casa.getId(), List.of(mercado.getId(), luz.getId(), Long.MAX_VALUE)))
                .extracting(Categoria::getNombre)
                .containsExactly("Luz", "Mercado");
    }
}
