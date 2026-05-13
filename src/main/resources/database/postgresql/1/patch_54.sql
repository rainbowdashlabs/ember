-- Rename SENDGRID to RAPIDMAIL
UPDATE ember_schema.station_mail_config SET provider = 'RAPIDMAIL' WHERE provider = 'SENDGRID';
