ALTER TABLE ${ohdsiSchema}.sec_user ADD origin VARCHAR(32) NULL;
UPDATE ${ohdsiSchema}.sec_user SET origin = 'SYSTEM';
ALTER TABLE ${ohdsiSchema}.sec_user ALTER COLUMN origin VARCHAR(32) NOT NULL;
ALTER TABLE ${ohdsiSchema}.sec_user ADD CONSTRAINT df_sec_user_origin DEFAULT 'SYSTEM' FOR origin;

ALTER TABLE ${ohdsiSchema}.sec_user_role ADD origin VARCHAR(32) NULL;
UPDATE ${ohdsiSchema}.sec_user_role SET origin = 'SYSTEM';
ALTER TABLE ${ohdsiSchema}.sec_user_role ALTER COLUMN origin VARCHAR(32) NOT NULL;
ALTER TABLE ${ohdsiSchema}.sec_user_role ADD CONSTRAINT df_sec_user_role_origin DEFAULT 'SYSTEM' FOR origin;
