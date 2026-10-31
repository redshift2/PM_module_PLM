-- Add nullable part metadata fields; existing parts retain NULL values.
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS description1 CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS description2 CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS description3 CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cost DECIMAL(19,4);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS weight DECIMAL(19,4);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS material VARCHAR(255);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS supplier VARCHAR(255);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS surface_treatment VARCHAR(255);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS lom VARCHAR(16);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS dcn VARCHAR(255);
