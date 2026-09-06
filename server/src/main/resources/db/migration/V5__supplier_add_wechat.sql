-- 供货商档案：新增可选「微信」联系方式（联系人沟通渠道），紧随 phone
ALTER TABLE supplier
    ADD COLUMN wechat VARCHAR(50) NULL COMMENT '微信' AFTER phone;
