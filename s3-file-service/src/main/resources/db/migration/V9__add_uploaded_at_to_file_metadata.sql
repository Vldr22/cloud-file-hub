ALTER TABLE file_metadata
    ADD COLUMN uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

COMMENT ON COLUMN file_metadata.uploaded_at IS 'Время загрузки файла';
