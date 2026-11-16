CREATE TABLE vehicle_photo_cleanup (
    public_id VARCHAR(255) PRIMARY KEY,
    retry_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_vehicle_photo_cleanup_retry_at ON vehicle_photo_cleanup (retry_at);
