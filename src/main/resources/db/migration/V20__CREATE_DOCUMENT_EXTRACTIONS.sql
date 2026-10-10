-- AI receipt scan: kết quả trích xuất từ ảnh/PDF chứng từ.
-- Liên kết với request + file_storages khi user gửi yêu cầu (request_id, file_id),
-- dùng để đối chiếu số tiền, phát hiện hoá đơn dùng lại và đo độ chính xác.

CREATE TABLE IF NOT EXISTS document_extractions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    file_hash        VARCHAR(64)   NOT NULL,
    file_name        VARCHAR(255),
    mime_type        VARCHAR(100)  NOT NULL,
    provider         VARCHAR(30)   NOT NULL,
    model            VARCHAR(100),
    total_amount     NUMERIC(19,2),
    invoice_date     DATE,
    vendor_name      VARCHAR(255),
    vendor_tax_code  VARCHAR(30),
    invoice_number   VARCHAR(100),
    vat_amount       NUMERIC(19,2),
    currency         VARCHAR(10),
    expense_summary  TEXT,
    line_items       JSONB,
    warnings         JSONB,
    raw_response     JSONB,
    request_id       BIGINT REFERENCES requests(id),
    file_id          BIGINT REFERENCES file_storages(id),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by       BIGINT,
    updated_by       BIGINT
);

CREATE INDEX IF NOT EXISTS idx_doc_extract_user_hash ON document_extractions(user_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_doc_extract_request   ON document_extractions(request_id);
CREATE INDEX IF NOT EXISTS idx_doc_extract_invoice   ON document_extractions(vendor_tax_code, invoice_number);
