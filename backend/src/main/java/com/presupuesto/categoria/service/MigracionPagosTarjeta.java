package com.presupuesto.categoria.service;

import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Al arrancar, crea el grupo y la categoría de pago de las tarjetas que aún no la tienen (las
 * anteriores a esta función). Es idempotente: solo actúa donde falta la categoría, así que
 * repetirla no crea ni cambia nada. No es transaccional en conjunto: cada presupuesto va en su
 * propia transacción (y bloquea solo ese presupuesto), de modo que un fallo se registra y no
 * deshace los demás ni tumba el arranque; la siguiente ejecución completa lo que faltó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MigracionPagosTarjeta implements ApplicationRunner {

    private final CategoriaRepository categoriaRepository;
    private final CategoriasPagoTarjeta categorias;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        migrar();
    }

    void migrar() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        List<Long> pendientes = categoriaRepository.presupuestosConTarjetasSinCategoria();
        for (Long presupuestoId : pendientes) {
            try {
                transaccion.executeWithoutResult(estado -> migrarPresupuesto(presupuestoId));
            } catch (RuntimeException fallo) {
                log.error("No se pudo crear las categorías de pago de tarjeta del presupuesto {}; "
                        + "se reintentará en el próximo arranque", presupuestoId, fallo);
            }
        }
    }

    private void migrarPresupuesto(Long presupuestoId) {
        for (Cuenta tarjeta : categoriaRepository.tarjetasSinCategoria(presupuestoId)) {
            categorias.crear(tarjeta);
        }
    }
}
