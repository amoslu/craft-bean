-- V4: 对齐 docs/database-schema.md §2 约定 —— 回到软外键（无 DB 级 FOREIGN KEY），单据/流水去掉 deleted

-- 1) 去掉真实外键约束（应用层已做存在性/状态校验，见 GreenBeanService / PurchaseService）
ALTER TABLE green_bean DROP FOREIGN KEY fk_green_bean_supplier;
ALTER TABLE green_lot DROP FOREIGN KEY fk_green_lot_bean;
ALTER TABLE green_lot DROP FOREIGN KEY fk_green_lot_supplier;
ALTER TABLE inventory_movement DROP FOREIGN KEY fk_inventory_green_bean;
ALTER TABLE inventory_movement DROP FOREIGN KEY fk_inventory_supplier;

-- 2) 单据 green_lot 用 status 作废、流水 inventory_movement append-only，均不加 deleted
ALTER TABLE green_lot DROP COLUMN deleted;
ALTER TABLE inventory_movement DROP COLUMN deleted;
