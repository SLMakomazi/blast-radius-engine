CREATE TABLE synthetic_documents (
    id UUID PRIMARY KEY,
    document_reference VARCHAR(64) NOT NULL,
    customer_reference VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id VARCHAR(128) NOT NULL
);
CREATE INDEX idx_synthetic_documents_correlation ON synthetic_documents (correlation_id);
