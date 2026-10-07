package com.presupuesto.transaccion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TransaccionSpecificationsTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 15);
    private static final LocalDate D3 = LocalDate.of(2026, 10, 1);

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
    private Cuenta efectivo;
    private Cuenta ajena;
    private Categoria comida;
    private Categoria ocio;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-spec@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        banco = cuentaRepository.save(nuevaCuenta(casa, "Banco"));
        efectivo = cuentaRepository.save(nuevaCuenta(casa, "Efectivo"));
        ajena = cuentaRepository.saveAndFlush(nuevaCuenta(viajes, "Banco"));
        comida = categoria(casa, "Comida");
        ocio = categoria(casa, "Ocio");
    }

    @Test
    void delPresupuestoDejaAfueraLasDeOtroPresupuesto() {
        Transaccion propia = guardar(banco, D1, -1L, null, null);
        guardar(ajena, D1, -2L, null, null);

        assertThat(buscar(TransaccionSpecifications.delPresupuesto(casa.getId())))
                .containsExactly(propia);
    }

    @Test
    void deLaCuentaFiltraPorCuenta() {
        Transaccion enBanco = guardar(banco, D1, -1L, null, null);
        guardar(efectivo, D1, -2L, null, null);

        assertThat(buscar(TransaccionSpecifications.deLaCuenta(banco.getId())))
                .containsExactly(enBanco);
    }

    @Test
    void deLaCategoriaCoincideConLaPropiaYConLasSubtransaccionesSinDuplicar() {
        Transaccion simple = guardar(banco, D1, -1L, null, null);
        simple.categorizar(comida);
        Transaccion dividida = guardar(banco, D2, -3L, null, null);
        dividida.reemplazarSubtransacciones(List.of(sub(-1L, comida), sub(-2L, comida)));
        Transaccion deOcio = guardar(banco, D3, -4L, null, null);
        deOcio.categorizar(ocio);
        transaccionRepository.flush();

        Page<Transaccion> pagina = transaccionRepository.findAll(
                TransaccionSpecifications.deLaCategoria(comida.getId()), PageRequest.of(0, 10));

        assertThat(pagina.getContent()).containsExactlyInAnyOrder(simple, dividida);
        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }

    @Test
    void desdeYHastaSonInclusivos() {
        guardar(banco, D1, -1L, null, null);
        Transaccion medio = guardar(banco, D2, -2L, null, null);
        Transaccion fin = guardar(banco, D3, -3L, null, null);

        assertThat(buscar(TransaccionSpecifications.desde(D2)))
                .containsExactlyInAnyOrder(medio, fin);
        assertThat(buscar(TransaccionSpecifications.desde(D2)
                        .and(TransaccionSpecifications.hasta(D2))))
                .containsExactly(medio);
        assertThat(buscar(TransaccionSpecifications.hasta(D1))).hasSize(1);
    }

    @Test
    void conEstadoYSinAprobar() {
        Transaccion conciliada = guardar(banco, D1, -1L, null, null);
        conciliada.cambiarEstado(EstadoTransaccion.CONCILIADA);
        Transaccion sinAprobar = transaccionRepository.save(Transaccion.builder()
                .cuenta(banco).fecha(D1).monto(-2L).aprobada(false).build());
        transaccionRepository.flush();

        assertThat(buscar(TransaccionSpecifications.conEstado(EstadoTransaccion.CONCILIADA)))
                .containsExactly(conciliada);
        assertThat(buscar(TransaccionSpecifications.sinAprobar())).containsExactly(sinAprobar);
    }

    @Test
    void contieneBuscaEnBeneficiarioYMemoSinDistinguirMayusculas() {
        Transaccion porBeneficiario = guardar(banco, D1, -1L, "Supermercado Norte", null);
        Transaccion porMemo = guardar(banco, D2, -2L, null, "compra norte");
        guardar(banco, D3, -3L, "Farmacia", "medicina");

        assertThat(buscar(TransaccionSpecifications.contiene("NORTE")))
                .containsExactlyInAnyOrder(porBeneficiario, porMemo);
    }

    @Test
    void contieneTrataLosComodinesComoTextoLiteral() {
        Transaccion porcentaje = guardar(banco, D1, -1L, null, "100% listo");
        Transaccion guion = guardar(banco, D2, -2L, "a_b", null);
        Transaccion barra = guardar(banco, D3, -3L, null, "ruta c:\\temp");
        guardar(banco, D3, -4L, "axb", "nada");

        assertThat(buscar(TransaccionSpecifications.contiene("%"))).containsExactly(porcentaje);
        assertThat(buscar(TransaccionSpecifications.contiene("_"))).containsExactly(guion);
        assertThat(buscar(TransaccionSpecifications.contiene("\\"))).containsExactly(barra);
        assertThat(buscar(TransaccionSpecifications.contiene("a_b"))).containsExactly(guion);
    }

    @Test
    void escaparComodinesAnteponeLaBarraAPorcientoGuionBajoYBarra() {
        assertThat(TransaccionSpecifications.escaparComodines("a%b_c\\d"))
                .isEqualTo("a\\%b\\_c\\\\d");
        assertThat(TransaccionSpecifications.escaparComodines("normal")).isEqualTo("normal");
    }

    @Test
    void losFiltrosSeCombinanYElOrdenEsFechaYLuegoIdDescendente() {
        Transaccion a = guardar(banco, D2, -1L, "Tienda", null);
        Transaccion b = guardar(banco, D2, -2L, "Tienda", null);
        Transaccion c = guardar(banco, D3, -3L, "Tienda", null);
        guardar(efectivo, D3, -4L, "Tienda", null);
        guardar(banco, D1, -5L, "Otro", null);

        Specification<Transaccion> filtros = TransaccionSpecifications
                .delPresupuesto(casa.getId())
                .and(TransaccionSpecifications.deLaCuenta(banco.getId()))
                .and(TransaccionSpecifications.desde(D2))
                .and(TransaccionSpecifications.contiene("tienda"));
        Sort orden = Sort.by(Sort.Direction.DESC, "fecha")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Transaccion> pagina =
                transaccionRepository.findAll(filtros, PageRequest.of(0, 10, orden));

        assertThat(pagina.getContent()).containsExactly(c, b, a);
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    /** Siempre dentro del presupuesto del test: la base puede tener datos de otras personas. */
    private List<Transaccion> buscar(Specification<Transaccion> filtro) {
        return transaccionRepository.findAll(
                TransaccionSpecifications.delPresupuesto(casa.getId()).and(filtro));
    }

    private Transaccion guardar(
            Cuenta cuenta, LocalDate fecha, long monto, String beneficiario, String memo) {
        Transaccion transaccion = Transaccion.builder()
                .cuenta(cuenta)
                .fecha(fecha)
                .monto(monto)
                .beneficiario(beneficiario)
                .memo(memo)
                .build();
        return transaccionRepository.saveAndFlush(transaccion);
    }

    private static SubTransaccion sub(long monto, Categoria categoria) {
        return SubTransaccion.builder().monto(monto).categoria(categoria).build();
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

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static Cuenta nuevaCuenta(Presupuesto presupuesto, String nombre) {
        return Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(TipoCuenta.CORRIENTE)
                .build();
    }
}
