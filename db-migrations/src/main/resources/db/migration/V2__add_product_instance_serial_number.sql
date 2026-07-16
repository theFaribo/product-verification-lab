alter table product_instance
    add column serial_number varchar(64);

-- Старые записи нельзя восстановить без исходного кода маркировки.
-- Синтетическое значение намеренно приведёт к INCONSISTENT_DATA при сверке
-- и не позволит молча считать потенциально устаревшие данные корректными.
update product_instance
set serial_number = 'LEGACY-' || left(code_hash, 16)
where serial_number is null;

alter table product_instance
    alter column serial_number set not null;
