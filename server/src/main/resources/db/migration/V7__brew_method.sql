-- 冲煮方式档案（生豆品种「推荐方向」选项源）+ 品种-冲煮方式关联

CREATE TABLE brew_method (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_brew_method_name (name),
    KEY idx_brew_method_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冲煮方式档案';

CREATE TABLE green_bean_brew_method (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    green_bean_id BIGINT NOT NULL,
    brew_method_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_green_bean_brew (green_bean_id, brew_method_id),
    KEY idx_gbbm_method (brew_method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生豆品种-冲煮方式（推荐方向）';

INSERT INTO brew_method (id, name, status) VALUES
    (1, '手冲', 'ACTIVE'),
    (2, '冰滴', 'ACTIVE'),
    (3, '意式', 'ACTIVE'),
    (4, '法压', 'ACTIVE'),
    (5, '冷萃', 'ACTIVE');
