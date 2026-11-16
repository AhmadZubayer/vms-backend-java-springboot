CREATE TABLE vehicles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_number VARCHAR(64) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    company VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    photo_url TEXT,
    photo_public_id VARCHAR(255)
);

CREATE UNIQUE INDEX uq_vehicles_vehicle_number
    ON vehicles (lower(btrim(vehicle_number)));