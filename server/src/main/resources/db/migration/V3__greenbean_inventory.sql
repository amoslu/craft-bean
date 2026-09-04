CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50) NULL,
    phone VARCHAR(30) NULL,
    address VARCHAR(255) NULL,
    main_category VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_supplier_name (name),
    KEY idx_supplier_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供货商档案';

CREATE TABLE green_bean (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    origin VARCHAR(100) NULL,
    process VARCHAR(50) NULL,
    variety VARCHAR(80) NULL,
    default_supplier_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_green_bean_name (name),
    KEY idx_green_bean_status (status, deleted),
    CONSTRAINT fk_green_bean_supplier FOREIGN KEY (default_supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生豆品种档案';

CREATE TABLE green_lot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    green_bean_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    purchase_date DATE NOT NULL,
    total_kg DECIMAL(12,3) NOT NULL,
    remaining_kg DECIMAL(12,3) NOT NULL,
    cost_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_green_lot_no (lot_no),
    KEY idx_green_lot_bean (green_bean_id, deleted),
    KEY idx_green_lot_supplier (supplier_id, deleted),
    CONSTRAINT fk_green_lot_bean FOREIGN KEY (green_bean_id) REFERENCES green_bean(id),
    CONSTRAINT fk_green_lot_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生豆采购批次';

CREATE TABLE inventory_movement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type VARCHAR(40) NOT NULL,
    batch_kind VARCHAR(20) NOT NULL DEFAULT 'GREEN',
    batch_id BIGINT NOT NULL,
    green_bean_id BIGINT NULL,
    supplier_id BIGINT NULL,
    change_kg DECIMAL(12,3) NOT NULL,
    unit_price DECIMAL(12,2) NULL,
    amount DECIMAL(14,2) NULL,
    ref_doc_no VARCHAR(50) NULL,
    operator_id BIGINT NULL,
    movement_date DATE NOT NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_inventory_batch (batch_kind, batch_id, created_at),
    KEY idx_inventory_bean (green_bean_id, created_at),
    KEY idx_inventory_date (movement_date),
    CONSTRAINT fk_inventory_green_bean FOREIGN KEY (green_bean_id) REFERENCES green_bean(id),
    CONSTRAINT fk_inventory_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水（只增不改）';
