package com.presupuesto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FormatoSqlTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void elSqlDeHibernateSeImprimeFormateadoEnVariasLineas() {
        PrintStream salidaOriginal = System.out;
        ByteArrayOutputStream capturada = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturada));

        try {
            entityManager
                    .createNativeQuery(
                            "select table_name, table_schema from information_schema.tables"
                                    + " where table_schema = 'public'")
                    .getResultList();
        } finally {
            System.setOut(salidaOriginal);
        }

        String salida = capturada.toString();
        assertThat(salida).containsIgnoringCase("select");
        assertThat(salida.split("\n").length).isGreaterThan(1);
    }
}
