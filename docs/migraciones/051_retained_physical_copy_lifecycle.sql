-- Retain physical-copy identity and manual receipt provenance after inventory removal.
-- Existing rows intentionally keep NULL lifecycle metadata.
ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS disposition_reason VARCHAR(40);

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS disposition_note TEXT;

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS disposed_at TIMESTAMP;

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS disposed_by VARCHAR(255);

ALTER TABLE disco_qr_copy
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
