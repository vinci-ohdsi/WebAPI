ALTER TABLE ${ohdsiSchema}.source ADD is_cache_enabled BIT NULL;
UPDATE ${ohdsiSchema}.source SET is_cache_enabled = 1;
ALTER TABLE ${ohdsiSchema}.source ALTER COLUMN is_cache_enabled BIT NOT NULL;
