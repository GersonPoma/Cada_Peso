package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Contra la base de datos (revierte al terminar), solo con presupuestos propios. */
@SpringBootTest
@Transactional
class CategoriasPagoTarjetaTest {

    private static final String NOMBRE_GRUPO = "Pagos de tarjetas de crédito";

    @Autowired
    private CategoriasPagoTarjeta categorias;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;

    @BeforeEach
    void crearPresupuesto() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-pago-tarjeta@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(Presupuesto.builder()
                .usuario(ana).nombre("Casa").nombreNormalizado("casa").moneda("BOB").build());
    }

    @Test
    void laPrimeraTarjetaCreaElGrupoDePagosAlFinalDelOrden() {
        grupo("Facturas", 0);
        grupo("Ahorro", 1);

        Categoria categoria = categorias.crear(tarjeta("Visa", false));

        GrupoCategoria pagos = categoria.getGrupo();
        assertThat(pagos.getTipo()).isEqualTo(TipoGrupoCategoria.PAGOS_TARJETA);
        assertThat(pagos.getNombre()).isEqualTo(NOMBRE_GRUPO);
        assertThat(pagos.getOrden()).isEqualTo(2);
        assertThat(categoria.getNombre()).isEqualTo("Pago: Visa");
        assertThat(categoria.isOculta()).isFalse();
        assertThat(categoria.getCuentaTarjeta().getNombre()).isEqualTo("Visa");
    }

    @Test
    void laSegundaTarjetaReutilizaElGrupo() {
        Categoria visa = categorias.crear(tarjeta("Visa", false));
        Categoria master = categorias.crear(tarjeta("Master", false));

        assertThat(master.getGrupo().getId()).isEqualTo(visa.getGrupo().getId());
        assertThat(master.getOrden()).isEqualTo(1);
        assertThat(grupoRepository.countByPresupuestoId(casa.getId())).isEqualTo(1);
    }

    @Test
    void soloLasTarjetasDeCreditoDelPresupuestoTienenCategoriaDePago() {
        assertThat(CategoriasPagoTarjeta.aplica(tarjeta("Visa", false))).isTrue();
        assertThat(CategoriasPagoTarjeta.aplica(
                cuenta("Seguimiento", TipoCuenta.TARJETA_CREDITO, true))).isFalse();
        assertThat(CategoriasPagoTarjeta.aplica(cuenta("Banco", TipoCuenta.CORRIENTE, true)))
                .isFalse();
    }

    @Test
    void crearEsIdempotente() {
        Cuenta visa = tarjeta("Visa", false);

        Categoria primera = categorias.crear(visa);
        Categoria segunda = categorias.crear(visa);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(categoriaRepository.countByGrupoId(primera.getGrupo().getId())).isEqualTo(1);
    }

    @Test
    void unaTarjetaCerradaRecibeSuCategoriaOculta() {
        Categoria categoria = categorias.crear(tarjeta("Vieja", true));

        assertThat(categoria.isOculta()).isTrue();
    }

    @Test
    void ocultarYMostrarSiguenALaTarjeta() {
        Cuenta visa = tarjeta("Visa", false);
        categorias.crear(visa);

        categorias.ocultar(visa);
        assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId()).orElseThrow()
                .isOculta()).isTrue();

        categorias.mostrar(visa);
        assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId()).orElseThrow()
                .isOculta()).isFalse();
    }

    @Test
    void renombrarLaTarjetaRenombraLaCategoriaConservandoSuId() {
        Cuenta visa = tarjeta("Visa", false);
        Categoria creada = categorias.crear(visa);

        visa.renombrar("Visa Oro");
        categorias.renombrar(visa);

        Categoria categoria = categoriaRepository.findByCuentaTarjetaId(visa.getId()).orElseThrow();
        assertThat(categoria.getId()).isEqualTo(creada.getId());
        assertThat(categoria.getNombre()).isEqualTo("Pago: Visa Oro");
        assertThat(categoria.getNombreNormalizado()).isEqualTo("pago: visa oro");
    }

    @Test
    void renombrarSoloCambiandoMayusculasNoAgregaSufijo() {
        Cuenta visa = tarjeta("Visa", false);
        categorias.crear(visa);

        visa.renombrar("VISA");
        categorias.renombrar(visa);

        assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId()).orElseThrow()
                .getNombre()).isEqualTo("Pago: VISA");
    }

    @Test
    void unaCategoriaAjenaConElMismoNombreNormalizadoAgregaElSufijoConElIdDeLaCuenta() {
        GrupoCategoria pagos = categorias.asegurarGrupo(casa.getId());
        categoriaRepository.saveAndFlush(Categoria.builder()
                .grupo(pagos).nombre("PAGO: VISA").nombreNormalizado("pago: visa").orden(0)
                .build());
        Cuenta visa = tarjeta("Visa", false);

        Categoria categoria = categorias.crear(visa);

        assertThat(categoria.getNombre()).isEqualTo("Pago: Visa (" + visa.getId() + ")");
    }

    @Test
    void conUnNombreDeCuentaDe100CaracteresLaCategoriaNoPasaDe100AlCrearYAlRenombrar() {
        Cuenta larga = tarjeta("a".repeat(100), false);

        Categoria categoria = categorias.crear(larga);
        assertThat(categoria.getNombre()).hasSize(100).startsWith("Pago: ");

        larga.renombrar("b".repeat(100));
        categorias.renombrar(larga);
        Categoria renombrada = categoriaRepository.findByCuentaTarjetaId(larga.getId())
                .orElseThrow();
        assertThat(renombrada.getNombre()).hasSize(100).startsWith("Pago: bbb");
    }

    @Test
    void elRecortePorCodePointsNoPartePaesSustitutos() {
        String emojis = "😀".repeat(100);
        Cuenta rara = tarjeta(emojis, false);

        Categoria categoria = categorias.crear(rara);

        String nombre = categoria.getNombre();
        assertThat(nombre.codePointCount(0, nombre.length())).isEqualTo(100);
        assertThat(nombre).startsWith("Pago: ");
        assertThat(Character.isLowSurrogate(nombre.charAt(nombre.length() - 1))).isTrue();
    }

    @Test
    void dosNombresLargosQueCoincidenTrasElRecorteQuedanDistintosYSinPasarDe100() {
        String comun = "x".repeat(97);
        Cuenta primera = tarjeta(comun + "111", false);
        Cuenta segunda = tarjeta(comun + "222", false);

        Categoria a = categorias.crear(primera);
        Categoria b = categorias.crear(segunda);

        assertThat(a.getNombre()).hasSize(100);
        assertThat(b.getNombre()).hasSizeLessThanOrEqualTo(100)
                .endsWith(" (" + segunda.getId() + ")");
        assertThat(b.getNombreNormalizado()).isNotEqualTo(a.getNombreNormalizado());
    }

    @Test
    void unGrupoNormalConElNombreOriginalNoSeTocaYElDePagosUsaElNombreAlterno() {
        GrupoCategoria normal = grupo(NOMBRE_GRUPO, 0);
        categoriaRepository.saveAndFlush(Categoria.builder()
                .grupo(normal).nombre("Otra").nombreNormalizado("otra").orden(0).build());

        Categoria categoria = categorias.crear(tarjeta("Visa", false));

        GrupoCategoria pagos = categoria.getGrupo();
        assertThat(pagos.getId()).isNotEqualTo(normal.getId());
        assertThat(pagos.getNombre()).isEqualTo(NOMBRE_GRUPO + " (2)");
        assertThat(pagos.getTipo()).isEqualTo(TipoGrupoCategoria.PAGOS_TARJETA);
        GrupoCategoria intacto = grupoRepository.findById(normal.getId()).orElseThrow();
        assertThat(intacto.getNombre()).isEqualTo(NOMBRE_GRUPO);
        assertThat(intacto.getTipo()).isEqualTo(TipoGrupoCategoria.NORMAL);
        assertThat(categoriaRepository.countByGrupoId(normal.getId())).isEqualTo(1);
    }

    @Test
    void siElNombreAlternoTambienEstaOcupadoSeUsaElSiguienteNumero() {
        grupo(NOMBRE_GRUPO, 0);
        grupo(NOMBRE_GRUPO + " (2)", 1);

        Categoria categoria = categorias.crear(tarjeta("Visa", false));

        assertThat(categoria.getGrupo().getNombre()).isEqualTo(NOMBRE_GRUPO + " (3)");
    }

    @Test
    void laSegundaTarjetaEncuentraElGrupoPorTipoAunqueHayaUnNormalConElNombreOriginal() {
        grupo(NOMBRE_GRUPO, 0);
        Categoria visa = categorias.crear(tarjeta("Visa", false));

        Categoria master = categorias.crear(tarjeta("Master", false));

        assertThat(master.getGrupo().getId()).isEqualTo(visa.getGrupo().getId());
        List<GrupoCategoria> grupos =
                grupoRepository.findByPresupuestoIdOrderByOrden(casa.getId());
        assertThat(grupos).extracting(GrupoCategoria::getTipo)
                .containsExactly(TipoGrupoCategoria.NORMAL, TipoGrupoCategoria.PAGOS_TARJETA);
    }

    private GrupoCategoria grupo(String nombre, int orden) {
        return grupoRepository.saveAndFlush(GrupoCategoria.builder()
                .presupuesto(casa)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build());
    }

    private Cuenta tarjeta(String nombre, boolean cerrada) {
        Cuenta cuenta = cuenta(nombre, TipoCuenta.TARJETA_CREDITO, false);
        if (cerrada) {
            cuenta.cerrar();
            cuentaRepository.saveAndFlush(cuenta);
        }
        return cuenta;
    }

    /** El tercer argumento es "de seguimiento" (fuera del presupuesto). */
    private Cuenta cuenta(String nombre, TipoCuenta tipo, boolean seguimiento) {
        return cuentaRepository.saveAndFlush(Cuenta.builder()
                .presupuesto(casa)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(tipo)
                .enPresupuesto(!seguimiento)
                .build());
    }
}
