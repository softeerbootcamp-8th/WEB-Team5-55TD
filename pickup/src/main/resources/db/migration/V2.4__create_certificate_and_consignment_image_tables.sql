CREATE TABLE IF NOT EXISTS certificate (
    certificate_id     BIGINT AUTO_INCREMENT,
    consignment_id     BIGINT NOT NULL,
    serial_number      VARCHAR(255) NOT NULL,
    grade              VARCHAR(255) NOT NULL,
    certification_body VARCHAR(255) NOT NULL,
    inspected_at        DATE NOT NULL,
    PRIMARY KEY (certificate_id),
    CONSTRAINT uk_certificate_serial_number UNIQUE (serial_number),
    CONSTRAINT uk_certificate_consignment_id UNIQUE (consignment_id),
    CONSTRAINT fk_certificate_consignment
        FOREIGN KEY (consignment_id) REFERENCES consignment (consignment_id)
);

CREATE TABLE IF NOT EXISTS consignment_image (
    consignment_image_id BIGINT AUTO_INCREMENT,
    consignment_id       BIGINT NOT NULL,
    image_order           INT NOT NULL,
    image_url              VARCHAR(255) NOT NULL,
    PRIMARY KEY (consignment_image_id),
    CONSTRAINT fk_consignment_image_consignment
        FOREIGN KEY (consignment_id) REFERENCES consignment (consignment_id)
);