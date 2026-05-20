-- Seed: 20 clientes, 20 ventas, 3 reservas
-- Idempotente: limpia datos previos antes de insertar

DELETE FROM envio WHERE id_envio > 0;
DELETE FROM venta WHERE id_venta > 0;
DELETE FROM reserva WHERE id_reserva > 0;
DELETE FROM cliente WHERE cedula IN (
  '41234567','42345678','43456789','44567890','45678901',
  '46789012','47890123','48901234','49012345','40123456',
  '41122334','42233445','43344556','44455667','45566778',
  '46677889','47788990','48899001','49900112','40011223'
);
UPDATE disco SET estado = 'DISPONIBLE' WHERE estado IN ('VENDIDO','RESERVADO');

-- ────────────────────────────────────────────
-- CLIENTES (20)
-- ────────────────────────────────────────────
INSERT INTO cliente (nombre, apellido, telefono, email, cedula, instagram_usuario, direccion, observaciones, activo, fecha_alta) VALUES
('Martín',       'Rodríguez',  '099111222', 'martin.rodriguez@gmail.com',  '41234567', '@martinrod_vinyl',    'Av. 18 de Julio 1234, Montevideo',              'Coleccionista de techno',         true, '2024-01-15 10:30:00'),
('Sofía',        'Pérez',      '098222333', 'sofiaperez@gmail.com',        '42345678', '@sofi.records',       'Bvar. Artigas 2150, Montevideo',                'Compra principalmente house',     true, '2024-02-03 14:20:00'),
('Federico',     'González',   '094333444', 'fede.gonzalez@gmail.com',     '43456789', '@fede_vinyl',         'Rambla Wilson 3400, Montevideo',                NULL,                              true, '2024-02-20 18:00:00'),
('Camila',       'Fernández',  '091444555', 'cami.fernandez@gmail.com',    '44567890', '@camifer.music',      'Av. Italia 4520, Montevideo',                   'Prefiere envíos al interior',     true, '2024-03-10 11:00:00'),
('Diego',        'Silva',      '095555666', 'diegosilva@gmail.com',        '45678901', '@dsilva_dj',          'Calle Joaquín Suárez 920, Canelones',           'DJ residente local',              true, '2024-03-25 16:45:00'),
('Lucía',        'Martínez',   '092666777', 'lucia.martinez@gmail.com',    '46789012', '@lumartinez',         'Calle Sarandí 432, Maldonado',                  NULL,                              true, '2024-04-08 09:15:00'),
('Andrés',       'López',      '099777888', 'andres.lopez@gmail.com',      '47890123', '@andreslp',           'Av. Real de San Carlos 110, Colonia',           'Cliente desde 2022',              true, '2024-05-12 13:30:00'),
('Valentina',    'García',     '098888999', 'valegarcia@gmail.com',        '48901234', '@valegarcia_vinyl',   'Calle Uruguay 880, Salto',                      NULL,                              true, '2024-06-01 17:20:00'),
('Joaquín',      'Sánchez',    '094999000', 'jsanchez@gmail.com',          '49012345', '@joacosanchez',       'Av. 19 de Abril 540, Paysandú',                 'Fan del IDM',                     true, '2024-06-18 10:00:00'),
('Florencia',    'Romero',     '091000111', 'flor.romero@gmail.com',       '40123456', '@flor.romero',        'Calle Sarandí 270, Rivera',                     NULL,                              true, '2024-07-04 15:40:00'),
('Matías',       'Suárez',     '095111000', 'matisuarez@gmail.com',        '41122334', '@matisuarezdj',       'Av. Artigas 1200, San José',                    NULL,                              true, '2024-08-22 12:10:00'),
('Antonella',    'Bentancur',  '092223344', 'antobentancur@gmail.com',     '42233445', '@anto.records',       'Calle Independencia 645, Florida',              'Compra usados, presupuesto bajo', true, '2024-09-09 11:25:00'),
('Bruno',        'Cabrera',    '099334455', 'bruno.cabrera@gmail.com',     '43344556', '@brunocab',           'Av. Costanera 88, Rocha',                       NULL,                              true, '2024-10-14 19:00:00'),
('Renata',       'Píriz',      '098445566', 'renata.piriz@gmail.com',      '44455667', '@renata_p',           'Calle Treinta y Tres 350, Mercedes, Soriano',   NULL,                              true, '2024-11-30 14:50:00'),
('Maximiliano',  'Acosta',     '094556677', 'max.acosta@gmail.com',        '45566778', '@maxiacosta',         'Av. Brigadier Aparicio Saravia 120, Tacuarembó','Pide reservas largas',            true, '2025-01-12 10:00:00'),
('Agustina',     'Ortiz',      '091667788', 'agusortiz@gmail.com',         '46677889', '@agusortizmusic',     'Calle 25 de Mayo 480, Durazno',                 NULL,                              true, '2025-02-25 16:15:00'),
('Nicolás',      'Bermúdez',   '095778899', 'nico.bermudez@gmail.com',     '47788990', '@nicob_vinyl',        'Av. Pedro Lascano 95, Melo, Cerro Largo',       NULL,                              true, '2025-04-07 13:00:00'),
('Paula',        'Cardozo',    '092889900', 'paula.cardozo@gmail.com',     '48899001', '@pauliii_c',          'Calle Treinta y Tres 200, Minas, Lavalleja',    'Le interesan reissues',           true, '2025-06-18 11:30:00'),
('Sebastián',    'Méndez',     '099990011', 'sebamendez@gmail.com',        '49900112', '@sebamendez_dj',      'Av. Manuel Lavalleja 60, Treinta y Tres',       'DJ profesional',                  true, '2025-08-05 18:45:00'),
('Carolina',     'Vázquez',    '098001122', 'carovazquez@gmail.com',       '40011223', '@carovr',             'Av. Lecueder 1100, Artigas',                    NULL,                              true, '2026-01-20 09:30:00');

-- ────────────────────────────────────────────
-- VENTAS (20) — distribuidas 2024(10) / 2025(6) / 2026(4)
-- ────────────────────────────────────────────

-- 2024: 10 ventas

-- Venta 1 — Mar 2024, Martín Rodríguez, Pulp Fiction, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='41234567'), 1, '2024-03-15 14:30:00', 'LOCAL', 2157.67, 'RETIRO', 'COMPLETADA', 'Pago en efectivo');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=1;

-- Venta 2 — Abr 2024, Diego Silva, DJ Compufunk A.I. Soul 2.0, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='45678901'), 4, '2024-04-08 11:00:00', 'LOCAL', 1112.23, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=4;

-- Venta 3 — Abr 2024, Sofía Pérez, Basic Bastard Vol. 3, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='42345678'), 6, '2024-04-22 16:45:00', 'INSTAGRAM', 1015.43, 'ENVIO', 'COMPLETADA', 'Coordinado por DM');
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Bvar. Artigas 2150, Montevideo', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=6;

-- Venta 4 — May 2024, Martín Rodríguez, Ildec Universos Paralelos, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='41234567'), 7, '2024-05-10 10:15:00', 'LOCAL', 850.87, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=7;

-- Venta 5 — Jun 2024, Lucía Martínez, FDEZ Open Your Bag, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='46789012'), 8, '2024-06-03 19:00:00', 'INSTAGRAM', 812.15, 'ENVIO', 'COMPLETADA', NULL);
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Calle Sarandí 432, Maldonado', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=8;

-- Venta 6 — Jul 2024, Diego Silva, Italo Deviance Purple Galaxy, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='45678901'), 10, '2024-07-19 13:30:00', 'LOCAL', 1160.63, 'RETIRO', 'COMPLETADA', 'Lleva 2 en total esta semana');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=10;

-- Venta 7 — Sep 2024, Joaquín Sánchez, SUCHI Ghungroo, INSTAGRAM, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='49012345'), 11, '2024-09-05 11:45:00', 'INSTAGRAM', 1286.47, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=11;

-- Venta 8 — Oct 2024, Martín Rodríguez, Droxal Spore Symphony, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='41234567'), 12, '2024-10-21 17:00:00', 'LOCAL', 1112.23, 'RETIRO', 'COMPLETADA', 'Cliente frecuente');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=12;

-- Venta 9 — Nov 2024, Sofía Pérez, DJ UNGEL Transpirits, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='42345678'), 15, '2024-11-12 20:30:00', 'INSTAGRAM', 918.63, 'ENVIO', 'COMPLETADA', NULL);
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Bvar. Artigas 2150, Montevideo', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=15;

-- Venta 10 — Dic 2024, Florencia Romero, Droxal Totem Pulse, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='40123456'), 16, '2024-12-07 15:00:00', 'LOCAL', 1383.27, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=16;

-- 2025: 6 ventas

-- Venta 11 — Feb 2025, Camila Fernández, Acid Synthesis Infinite Cycles, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='44567890'), 17, '2025-02-14 10:00:00', 'INSTAGRAM', 1112.23, 'ENVIO', 'COMPLETADA', 'San Valentín');
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Av. Italia 4520, Montevideo', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=17;

-- Venta 12 — Abr 2025, Sebastián Méndez, Swayzak Goose, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='49900112'), 21, '2025-04-03 12:00:00', 'LOCAL', 1015.43, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=21;

-- Venta 13 — Jun 2025, Agustina Ortiz, The Hacker Laser & Smoke, INSTAGRAM, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='46677889'), 22, '2025-06-20 18:15:00', 'INSTAGRAM', 1141.27, 'RETIRO', 'COMPLETADA', 'Pedido por IG');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=22;

-- Venta 14 — Ago 2025, Sebastián Méndez, Ambrose Cat Groove, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='49900112'), 23, '2025-08-15 11:30:00', 'LOCAL', 996.07, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=23;

-- Venta 15 — Oct 2025, Nicolás Bermúdez, Rick Wade Night Trackin, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='47788990'), 24, '2025-10-09 14:00:00', 'INSTAGRAM', 1044.47, 'ENVIO', 'COMPLETADA', NULL);
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Av. Pedro Lascano 95, Melo, Cerro Largo', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=24;

-- Venta 16 — Dic 2025, Lucía Martínez, RICK 8 Interactive DJ'ng, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='46789012'), 26, '2025-12-18 16:00:00', 'LOCAL', 1431.67, 'RETIRO', 'COMPLETADA', 'Regalo de fin de año');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=26;

-- 2026: 4 ventas

-- Venta 17 — Ene 2026, Diego Silva, Asphalt DJ SNTMNT001, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='45678901'), 27, '2026-01-10 13:00:00', 'LOCAL', 1102.55, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=27;

-- Venta 18 — Feb 2026, Paula Cardozo, General Electrix 3, INSTAGRAM, ENVIO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='48899001'), 30, '2026-02-05 10:30:00', 'INSTAGRAM', 1189.67, 'ENVIO', 'COMPLETADA', NULL);
INSERT INTO envio (id_venta, direccion_envio, estado_logistico)
VALUES (currval('venta_id_venta_seq'), 'Calle Treinta y Tres 200, Minas, Lavalleja', 'ENTREGADO');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=30;

-- Venta 19 — Mar 2026, Joaquín Sánchez, Anders Hajem Myr EP, LOCAL, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='49012345'), 31, '2026-03-22 11:00:00', 'LOCAL', 1092.87, 'RETIRO', 'COMPLETADA', NULL);
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=31;

-- Venta 20 — Abr 2026, Carolina Vázquez, Aphex Twin I Care Because You Do, INSTAGRAM, RETIRO
INSERT INTO venta (id_cliente, id_disco, fecha_venta, canal_venta, total, tipo_entrega, estado, observaciones)
VALUES ((SELECT id_cliente FROM cliente WHERE cedula='40011223'), 48, '2026-04-14 17:45:00', 'INSTAGRAM', 3145.03, 'RETIRO', 'COMPLETADA', 'Reissue esperada');
UPDATE disco SET estado='VENDIDO', fecha_actualizacion=NOW() WHERE id_disco=48;

-- ────────────────────────────────────────────
-- RESERVAS (3) — sobre discos DISPONIBLE
-- ────────────────────────────────────────────

-- Reserva 1 — Maximiliano Acosta, DFX Relax Your Body
INSERT INTO reserva (id_cliente, id_disco, fecha_reserva, fecha_vencimiento, senia, estado)
VALUES (
  (SELECT id_cliente FROM cliente WHERE cedula='45566778'),
  32,
  NOW() - INTERVAL '5 days',
  NOW() + INTERVAL '10 days',
  500.00,
  'ACTIVA'
);
UPDATE disco SET estado='RESERVADO', fecha_actualizacion=NOW() WHERE id_disco=32;

-- Reserva 2 — Andrés López, Zulu Matrix Hyperspace
INSERT INTO reserva (id_cliente, id_disco, fecha_reserva, fecha_vencimiento, senia, estado)
VALUES (
  (SELECT id_cliente FROM cliente WHERE cedula='47890123'),
  35,
  NOW() - INTERVAL '2 days',
  NOW() + INTERVAL '13 days',
  300.00,
  'ACTIVA'
);
UPDATE disco SET estado='RESERVADO', fecha_actualizacion=NOW() WHERE id_disco=35;

-- Reserva 3 — Valentina García, Talismann Kliniek 3
INSERT INTO reserva (id_cliente, id_disco, fecha_reserva, fecha_vencimiento, senia, estado)
VALUES (
  (SELECT id_cliente FROM cliente WHERE cedula='48901234'),
  38,
  NOW() - INTERVAL '1 day',
  NOW() + INTERVAL '14 days',
  400.00,
  'ACTIVA'
);
UPDATE disco SET estado='RESERVADO', fecha_actualizacion=NOW() WHERE id_disco=38;
