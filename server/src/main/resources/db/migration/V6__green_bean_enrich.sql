-- 生豆品种档案扩充：类型判别（单品/拼配）+ 编号/产区/产季/风味/配方/色值/含水率/海拔/包装规格/推荐烘焙度
ALTER TABLE green_bean
    ADD COLUMN code VARCHAR(50) NULL COMMENT '编号（手动·可空，不查重）' AFTER id,
    ADD COLUMN bean_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE=单品 / BLEND=拼配' AFTER code,
    ADD COLUMN region VARCHAR(100) NULL COMMENT '产区（国家下一级，如 耶加雪菲，仅单品）' AFTER origin,
    ADD COLUMN season VARCHAR(30) NULL COMMENT '产季（如 2025/2026，仅单品）' AFTER variety,
    ADD COLUMN flavor VARCHAR(500) NULL COMMENT '风味/杯测描述（两类型共用）' AFTER season,
    ADD COLUMN recipe VARCHAR(500) NULL COMMENT '配方（自由文本，仅拼配）' AFTER flavor,
    ADD COLUMN agtron_color INT NULL COMMENT '推荐色值（Agtron 0-100，仅拼配）' AFTER recipe,
    ADD COLUMN moisture_pct DECIMAL(5,2) NULL COMMENT '含水率 %（0-100，仅单品）' AFTER agtron_color,
    ADD COLUMN altitude_m INT NULL COMMENT '海拔 m（仅单品）' AFTER moisture_pct,
    ADD COLUMN recommended_roast VARCHAR(20) NULL COMMENT '推荐烘焙度：浅/中浅/中/中深/深' AFTER altitude_m,
    ADD COLUMN package_spec VARCHAR(100) NULL COMMENT '包装规格（如 60kg/袋）' AFTER recommended_roast;
