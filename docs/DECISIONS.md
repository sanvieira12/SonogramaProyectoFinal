# Decisiones Técnicas

## Por qué Spring Boot

- Ecosistema maduro con JPA, validación y REST out-of-the-box.
- Integración directa con AWS (futura migración a Elastic Beanstalk o ECS).
- Lombok reduce el boilerplate en entidades sin perder tipado fuerte.
- Alternativa considerada: Node.js/Express — descartado por menor soporte de tipos y ORM menos maduro para este equipo.

## Por qué PostgreSQL

- ACID compliant, ideal para cuenta corriente y transacciones financieras.
- Soporte nativo de tipos de datos avanzados (JSONB para futuras extensiones).
- Docker image oficial estable (postgres:16).
- Alternativa considerada: MySQL — descartado por diferencias de manejo de transacciones y menor soporte en Hibernate Dialects modernos.

## Por qué React + Vite

- Vite es significativamente más rápido que CRA para hot-reload en desarrollo.
- React tiene el mayor ecosistema de componentes UI para dashboards administrativos.
- Tailwind CSS permite iterar UI sin salir del JSX.

## Por qué Lombok

- Elimina getters, setters, constructores y toString repetitivos.
- No afecta la compilación en producción (scope optional).
- Compatible con las versiones de Spring Boot 3.x y Java 21.

## Java 21 (vs Java 17 del prompt)

- Java 21 es LTS y está instalado en el sistema.
- Spring Boot 3.2.x es compatible con Java 21.
- Virtual threads disponibles como mejora futura de rendimiento.

## ddl-auto=update en desarrollo

- Permite que JPA cree y actualice tablas automáticamente en `dev`.
- En producción usar `validate` con migraciones Flyway para control explícito del schema.
