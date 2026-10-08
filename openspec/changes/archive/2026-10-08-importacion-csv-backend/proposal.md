## Why

Hoy toda transacción se registra a mano. Quien quiere traer el historial de su banco debe
copiar fila por fila, que es el mayor obstáculo para adoptar la app. Los bancos exportan CSV;
importarlo con una vista previa, detección de duplicados y creación atómica cubre el caso de
uso central sin riesgo de ensuciar la cuenta con datos a medias.

## What Changes

- Nueva feature backend `importacion` con dos endpoints bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/importacion`:
  - `POST /vista-previa` (multipart: `archivo` + parámetros de mapeo): parsea y devuelve, por
    fila, los valores interpretados, su estado (`NUEVA`, `DUPLICADA`, `INVALIDA` con motivo) y
    totales (incluido `sinCategoria`). No guarda nada.
  - `POST` (multipart, mismos parámetros): repite el parseo en el servidor (nunca confía en una
    vista previa anterior) y, en **una** transacción de base de datos, crea las filas `NUEVA`.
    Las duplicadas se omiten; si hay alguna `INVALIDA` responde `422` sin crear nada, salvo con
    `omitirInvalidas=true`. Responde `201` con `{importadas, duplicadas, omitidas, sinCategoria}`.
- Mapeo por parámetros (el cliente los reenvía en cada llamada): separador, encabezado, columna
  y formato de fecha (lista cerrada), monto en una columna con signo o en dos (débito y
  crédito), separador decimal y de miles, columna de descripción (beneficiario) y de memo.
- Límites configurables (2 MB y 5000 filas por defecto), solo UTF-8 (con o sin BOM), parser CSV
  propio sin dependencias nuevas, y el archivo nunca toca el disco.
- Duplicado = misma fecha, mismo monto y mismo beneficiario normalizado en la cuenta, contando
  ocurrencias (dos cafés iguales el mismo día son legítimos).
- `TransaccionService` gana métodos públicos de lote (creación y conteo de existentes por
  clave); se reutilizan las reglas de una transacción manual, sin duplicar lógica.
- Bloqueo pesimista de la cuenta durante la importación para que dos importaciones simultáneas
  del mismo archivo no dupliquen.
- Fuera de alcance: guardar el mapeo en el servidor, frontend, formatos distintos de CSV
  (OFX, QIF, XLSX), deshacer una importación, categorización automática, duplicados difusos
  (fechas cercanas), importación de transferencias y de subtransacciones.

No hay cambios incompatibles (**BREAKING**): no cambia ningún endpoint ni respuesta existente.

## Capabilities

### New Capabilities
- `importacion-csv`: importación de transacciones a una cuenta desde un CSV de banco, con vista
  previa, reglas de duplicados, límites y creación atómica.

### Modified Capabilities
<!-- Ninguna: los requisitos de `transacciones` no cambian; solo se agregan métodos internos. -->

## Impact

- Backend: paquete nuevo `com.presupuesto.importacion` (`controller`, `service`, `dto/request`,
  `dto/response`); en `transaccion` se agregan `crearLote`, el conteo por clave, la consulta
  agrupada del repositorio y `ClaveMovimiento`.
- `comun/excepcion/ManejadorGlobalExcepciones`: manejadores de archivo demasiado grande y de
  parte `archivo` ausente (ambos `400 DATOS_INVALIDOS`, mensaje fijo).
- `application.properties`: `importacion.max-bytes`, `importacion.max-filas` y el techo
  multipart (`spring.servlet.multipart.*`, con el umbral en memoria).
- Sin dependencias nuevas en `pom.xml`, sin cambios de esquema de base de datos, sin cambios de
  frontend. La línea base (1381 tests) debe seguir pasando sin tocar ninguna aserción.
