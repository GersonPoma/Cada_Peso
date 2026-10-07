package com.presupuesto.transaccion.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.cuenta.entity.Cuenta;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransaccionTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 2);

    private static Transaccion nueva() {
        return Transaccion.builder()
                .cuenta(Cuenta.builder().build())
                .fecha(HOY)
                .monto(-1000L)
                .build();
    }

    private static SubTransaccion sub(long monto) {
        return SubTransaccion.builder().monto(monto).build();
    }

    @Test
    void losValoresPorDefectoSonNoConciliadaAprobadaYSinSubtransacciones() {
        Transaccion transaccion = nueva();

        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(transaccion.isAprobada()).isTrue();
        assertThat(transaccion.getSubtransacciones()).isEmpty();
        assertThat(transaccion.tieneSubtransacciones()).isFalse();
        assertThat(transaccion.estaReconciliada()).isFalse();
    }

    @Test
    void editarCambiaFechaMontoCategoriaBeneficiarioYMemo() {
        Transaccion transaccion = nueva();
        Categoria categoria = Categoria.builder().build();
        Beneficiario tienda = Beneficiario.builder().nombre("Tienda").build();

        transaccion.editar(HOY.plusDays(1), 2500L, categoria, tienda, "nota");

        assertThat(transaccion.getFecha()).isEqualTo(HOY.plusDays(1));
        assertThat(transaccion.getMonto()).isEqualTo(2500L);
        assertThat(transaccion.getCategoria()).isSameAs(categoria);
        assertThat(transaccion.getBeneficiario()).isEqualTo("Tienda");
        assertThat(transaccion.getBeneficiarioVinculado()).isSameAs(tienda);
        assertThat(transaccion.getMemo()).isEqualTo("nota");
    }

    @Test
    void editarSinBeneficiarioQuitaElVinculoYElTexto() {
        Beneficiario tienda = Beneficiario.builder().nombre("Tienda").build();
        Transaccion transaccion = Transaccion.builder()
                .cuenta(Cuenta.builder().build())
                .fecha(HOY)
                .monto(-1000L)
                .beneficiario("Tienda")
                .beneficiarioVinculado(tienda)
                .build();

        transaccion.editar(HOY, -1000L, null, null, null);

        assertThat(transaccion.getBeneficiario()).isNull();
        assertThat(transaccion.getBeneficiarioVinculado()).isNull();
    }

    @Test
    void editarDeriveElTextoDelNombreDelBeneficiarioVinculado() {
        Transaccion transaccion = Transaccion.builder()
                .cuenta(Cuenta.builder().build())
                .fecha(HOY)
                .monto(-1000L)
                .beneficiario("texto anterior sin vinculo")
                .build();
        Beneficiario netflix = Beneficiario.builder().nombre("Netflix").build();

        transaccion.editar(HOY, -1000L, null, netflix, null);

        assertThat(transaccion.getBeneficiario()).isEqualTo("Netflix");
        assertThat(transaccion.getBeneficiarioVinculado()).isSameAs(netflix);
    }

    @Test
    void sinVinculoElBeneficiarioVinculadoEsNulo() {
        assertThat(nueva().getBeneficiarioVinculado()).isNull();
    }

    @Test
    void moverACambiaLaCuenta() {
        Transaccion transaccion = nueva();
        Cuenta otra = Cuenta.builder().build();

        transaccion.moverA(otra);

        assertThat(transaccion.getCuenta()).isSameAs(otra);
    }

    @Test
    void categorizarCambiaLaCategoria() {
        Transaccion transaccion = nueva();
        Categoria categoria = Categoria.builder().build();

        transaccion.categorizar(categoria);

        assertThat(transaccion.getCategoria()).isSameAs(categoria);
    }

    @Test
    void aprobarEsIdempotente() {
        Transaccion transaccion = Transaccion.builder()
                .cuenta(Cuenta.builder().build())
                .fecha(HOY)
                .monto(1L)
                .aprobada(false)
                .build();

        transaccion.aprobar();
        transaccion.aprobar();

        assertThat(transaccion.isAprobada()).isTrue();
    }

    @Test
    void cambiarEstadoYEstaReconciliada() {
        Transaccion transaccion = nueva();

        transaccion.cambiarEstado(EstadoTransaccion.CONCILIADA);
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(transaccion.estaReconciliada()).isFalse();

        transaccion.cambiarEstado(EstadoTransaccion.RECONCILIADA);
        assertThat(transaccion.estaReconciliada()).isTrue();
    }

    @Test
    void reemplazarSubtransaccionesEnlazaLasNuevasYDescartaLasAnteriores() {
        Transaccion transaccion = nueva();
        SubTransaccion a = sub(-400L);
        SubTransaccion b = sub(-600L);
        transaccion.reemplazarSubtransacciones(List.of(a, b));

        assertThat(transaccion.getSubtransacciones()).containsExactly(a, b);
        assertThat(a.getTransaccion()).isSameAs(transaccion);
        assertThat(b.getTransaccion()).isSameAs(transaccion);
        assertThat(transaccion.tieneSubtransacciones()).isTrue();

        SubTransaccion c = sub(-1000L);
        transaccion.reemplazarSubtransacciones(List.of(c));

        assertThat(transaccion.getSubtransacciones()).containsExactly(c);
        assertThat(c.getTransaccion()).isSameAs(transaccion);

        transaccion.reemplazarSubtransacciones(List.of());
        assertThat(transaccion.tieneSubtransacciones()).isFalse();
    }

    @Test
    void reemplazarConLaMismaListaDeLaEntidadNoLaPierde() {
        Transaccion transaccion = nueva();
        transaccion.reemplazarSubtransacciones(List.of(sub(-400L), sub(-600L)));

        transaccion.reemplazarSubtransacciones(transaccion.getSubtransacciones());

        assertThat(transaccion.getSubtransacciones()).hasSize(2);
    }

    @Test
    void noExponeSettersDeNegocio() {
        List<String> setters = java.util.Arrays.stream(Transaccion.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(n -> n.startsWith("set"))
                .toList();

        assertThat(setters).isEmpty();
    }

    @Test
    void enlazarYDesenlazarCambianElPar() {
        Transaccion salida = nueva();
        Transaccion entrada = nueva();

        assertThat(salida.esTransferencia()).isFalse();
        assertThat(salida.getTransaccionPar()).isNull();

        salida.enlazarCon(entrada);
        entrada.enlazarCon(salida);

        assertThat(salida.esTransferencia()).isTrue();
        assertThat(salida.getTransaccionPar()).isSameAs(entrada);
        assertThat(entrada.getTransaccionPar()).isSameAs(salida);

        salida.desenlazar();

        assertThat(salida.esTransferencia()).isFalse();
        assertThat(salida.getTransaccionPar()).isNull();
    }
}
