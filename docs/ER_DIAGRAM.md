# Diagrama Entidad-Relación (Sprint 1)

## Entidades

### usuario
| Columna | Tipo | Restricciones |
|---|---|---|
| id_usuario | BIGSERIAL | PK |
| nombre_usuario | VARCHAR | NOT NULL, UNIQUE |
| email | VARCHAR | UNIQUE |
| activo | BOOLEAN | NOT NULL, default true |
| fecha_alta | TIMESTAMP | |

### cliente
| Columna | Tipo | Restricciones |
|---|---|---|
| id_cliente | BIGSERIAL | PK |
| nombre | VARCHAR | NOT NULL |
| apellido | VARCHAR | |
| telefono | VARCHAR | |
| email | VARCHAR | |
| cedula | VARCHAR | |
| instagram_usuario | VARCHAR | |
| direccion | VARCHAR | |
| observaciones | TEXT | |
| fecha_alta | TIMESTAMP | |

### disco
| Columna | Tipo | Restricciones |
|---|---|---|
| id_disco | BIGSERIAL | PK |
| codigo_interno | VARCHAR | |
| artista | VARCHAR | NOT NULL |
| album | VARCHAR | NOT NULL |
| genero | VARCHAR | |
| sello_discografico | VARCHAR | |
| descripcion | TEXT | |
| anio | INTEGER | |
| condicion | VARCHAR | NUEVO, USADO, CONSIGNACION, CATALOGO |
| tipo_disco | VARCHAR | VINILO, CD, DIGITAL, CASSETTE, OTRO |
| costo | NUMERIC(10,2) | |
| precio_venta | NUMERIC(10,2) | |
| estado | VARCHAR | NOT NULL, DISPONIBLE, RESERVADO, VENDIDO, SIN_STOCK |
| fecha_ingreso | TIMESTAMP | |
| fecha_actualizacion | TIMESTAMP | |

### direccion_cliente
| Columna | Tipo | Restricciones |
|---|---|---|
| id_direccion | BIGSERIAL | PK |
| id_cliente | BIGINT | FK → cliente |
| direccion | VARCHAR | NOT NULL |
| departamento | VARCHAR | |
| referencia | VARCHAR | |
| fecha_alta | TIMESTAMP | |
| ultima_usada | TIMESTAMP | |
| activa | BOOLEAN | NOT NULL |

### venta
| Columna | Tipo | Restricciones |
|---|---|---|
| id_venta | BIGSERIAL | PK |
| id_cliente | BIGINT | FK → cliente |
| id_disco | BIGINT | FK → disco |
| fecha_venta | TIMESTAMP | NOT NULL |
| canal_venta | VARCHAR | LOCAL, INSTAGRAM |
| total | NUMERIC(10,2) | |
| costo_disco | NUMERIC(10,2) | |
| precio_venta | NUMERIC(10,2) | |
| costo_envio | NUMERIC(10,2) | |
| porcentaje_impuesto | NUMERIC(5,2) | |
| monto_impuesto | NUMERIC(10,2) | |
| otros_costos | NUMERIC(10,2) | |
| total_final | NUMERIC(10,2) | |
| ganancia_estimada | NUMERIC(10,2) | |
| tipo_entrega | VARCHAR | RETIRO, ENVIO |
| estado | VARCHAR | PENDIENTE, COMPLETADA, CANCELADA |
| observaciones | TEXT | |

### envio
| Columna | Tipo | Restricciones |
|---|---|---|
| id_envio | BIGSERIAL | PK |
| id_venta | BIGINT | FK → venta, UNIQUE |
| direccion_envio | VARCHAR | NOT NULL |
| departamento | VARCHAR | |
| sucursal_dac_codigo | VARCHAR | |
| sucursal_dac_nombre | VARCHAR | |
| costo_envio | NUMERIC(10,2) | |
| estado_logistico | VARCHAR | PREPARANDO, EN_CAMINO, ENTREGADO, DEVUELTO |
| numero_seguimiento | VARCHAR | |
| fecha_envio | TIMESTAMP | |
| fecha_entrega | TIMESTAMP | |
| observaciones | TEXT | |

### reserva
| Columna | Tipo | Restricciones |
|---|---|---|
| id_reserva | BIGSERIAL | PK |
| id_cliente | BIGINT | FK → cliente |
| id_disco | BIGINT | FK → disco |
| fecha_reserva | TIMESTAMP | |
| fecha_vencimiento | TIMESTAMP | |
| senia | NUMERIC(10,2) | |
| estado | VARCHAR | ACTIVA, EXPIRADA, COMPRADA |

## Relaciones

```
cliente ──< venta    (un cliente puede tener muchas ventas)
cliente ──< direccion_cliente (un cliente puede tener muchas direcciones)
cliente ──< reserva  (un cliente puede tener muchas reservas)
disco   ──< reserva  (un disco puede tener muchas reservas)
disco   ──< venta    (un disco vendido queda asociado a una venta)
venta   ── envio     (una venta con envío tiene un registro logístico)
```
