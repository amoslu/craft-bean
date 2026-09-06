-- 用户安全改造：登录三要素（用户名 + 密码 + 手机号后四位）。
-- phone 为空表示存量/未绑定账号，登录尾号默认 0000 兜底；管理员补填后按末四位校验。
ALTER TABLE sys_user
    ADD COLUMN phone VARCHAR(20) NULL COMMENT '手机号(空则登录尾号默认0000)' AFTER name;
