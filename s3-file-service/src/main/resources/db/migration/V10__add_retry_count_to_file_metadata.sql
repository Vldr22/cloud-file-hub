ALTER TABLE file_metadata
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN file_metadata.retry_count IS 'Количество повторных отправок на сканирование планировщиком зависших файлов';
