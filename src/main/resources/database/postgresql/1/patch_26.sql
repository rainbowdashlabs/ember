INSERT INTO ember_schema.station_permission (name)
VALUES ('INVENTORY_ASSIGN'),
       ('INVENTORY_STORAGE'),
       ('PAGE_FORMS_VIEW'),
       ('PAGE_POLLS_VIEW'),
       ('CHECKLIST_READ'),
       ('CHECKLIST_MANAGE'),
       ('CHECKLIST_MANAGER')
ON CONFLICT (name) DO NOTHING;
