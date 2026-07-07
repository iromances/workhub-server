DELETE FROM sys_role_permission
WHERE permission_code = 'payment:secret:create';

DELETE FROM sys_permission
WHERE permission_code = 'payment:secret:create';
