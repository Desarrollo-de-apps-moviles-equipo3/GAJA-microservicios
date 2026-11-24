-- Ejecutar UNA vez en el SQL Editor de Supabase antes de levantar el servicio.
-- Hibernate crea las tablas solo (ddl-auto=update), pero necesita que el esquema exista.
create schema if not exists usuarios;
