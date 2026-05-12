-- Rename role table entries to enum names
UPDATE role SET name = 'LOGIN' WHERE name = 'login';
UPDATE role SET name = 'MEMBER' WHERE name = 'member';
UPDATE role SET name = 'MEMBER_MANAGER' WHERE name = 'member_manager';
UPDATE role SET name = 'TEAM' WHERE name = 'team';
UPDATE role SET name = 'ATTENDENCE_MANAGEMENT' WHERE name = 'attendance_management';
UPDATE role SET name = 'ATTENDENCE_EXPORT_MANAGER' WHERE name = 'attendance_export_manager';
UPDATE role SET name = 'INVENTORY_MANAGEMENT' WHERE name = 'inventory_management';
UPDATE role SET name = 'EVENT_MANAGEMENT' WHERE name = 'event_management';
UPDATE role SET name = 'MEMBER_MANAGEMENT' WHERE name = 'member_management';
UPDATE role SET name = 'MANAGER' WHERE name = 'manager';
UPDATE role SET name = 'ADMIN' WHERE name = 'admin';

-- Rename account_role entries to enum names
UPDATE account_role SET role = 'ADMIN' WHERE role = 'admin';
