-- 身份统一收敛到 sys_user 后，业务线成员表的展示名冗余列不再维护，
-- 展示名统一通过 user_name 关联 sys_user 获取。
ALTER TABLE pm_business_line_member DROP COLUMN member_display_name;
