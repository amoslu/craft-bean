ALTER TABLE sys_user
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    ADD COLUMN locked_until   DATETIME NULL COMMENT '锁定截止时间';

CREATE TABLE audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT       NULL COMMENT '操作人用户ID(系统操作可为空)',
    action      VARCHAR(50)  NOT NULL COMMENT '动作编码, 如 LOGIN/USER_CREATE',
    target_no   VARCHAR(100) NULL COMMENT '关联对象编号',
    detail      VARCHAR(500) NULL COMMENT '关键信息',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operator_created (operator_id, created_at),
    KEY idx_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志';
