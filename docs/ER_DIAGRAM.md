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
| anio | INTEGER | |
| condicion | VARCHAR | NUEVO, USADO, CONSIGNACION, CATALOGO |
| tipo_disco | VARCHAR | VINILO, CD, DIGITAL |
| costo | NUMERIC(10,2) | |
| precio_venta | NUMERIC(10,2) | |
| estado | VARCHAR | NOT NULL, DISPONIBLE, RESERVADO, VENDIDO, DESCONTINUADO |
| fecha_ingreso | TIMESTAMP | |
| fecha_actualizacion | TIMESTAMP | |

### venta
| Columna | Tipo | Restricciones |
|---|---|---|
| id_venta | BIGSERIAL | PK |
| id_cliente | BIGINT | FK → cliente |
| fecha_venta | TIMESTAMP | NOT NULL |
| canal_venta | VARCHAR | LOCAL, INSTAGRAM |
| total | NUMERIC(10,2) | |
| tipo_entrega | VARCHAR | RETIRO, ENVIO |
| estado | VARCHAR | PENDIENTE, COMPLETADA, CANCELADA |
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
cliente ──< reserva  (un cliente puede tener muchas reservas)
disco   ──< reserva  (un disco puede tener muchas reservas)
```
