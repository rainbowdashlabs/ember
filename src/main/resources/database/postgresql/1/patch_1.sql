-- Remove image data from database; images are now stored on disk via ImageService
ALTER TABLE ember_schema.station_member DROP COLUMN IF EXISTS avatar;
ALTER TABLE ember_schema.station_member DROP COLUMN IF EXISTS avatar_content_type;

ALTER TABLE ember_schema.lost_and_found_item DROP COLUMN IF EXISTS image;
ALTER TABLE ember_schema.lost_and_found_item DROP COLUMN IF EXISTS image_content_type;
