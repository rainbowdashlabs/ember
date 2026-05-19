-- Rename member_manager role to guardian
UPDATE ember_schema.role SET name = 'guardian' WHERE name = 'member_manager';

-- Rename profile field scope
UPDATE ember_schema.profile_field SET scope = 'GUARDIAN' WHERE scope = 'MEMBER_MANAGER';
