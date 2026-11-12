-- Worker-derived metadata is revision-specific; never overwrite user-edited material or weight.
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_metadata_json CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_bom_json CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_assembly_json CLOB;
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_volume_mm3 DECIMAL(24,8);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_length_unit VARCHAR(32);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_material_name VARCHAR(255);
ALTER TABLE plm_free_cad_part ADD COLUMN IF NOT EXISTS cad_document_kind VARCHAR(32);
