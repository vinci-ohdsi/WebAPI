IF NOT EXISTS (
    SELECT 1
    FROM sys.tables t
    WHERE t.name = 'tool'
      AND t.schema_id = schema_id('${ohdsiSchema}')
)
BEGIN
  CREATE TABLE ${ohdsiSchema}.tool
  (
    id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    description VARCHAR(1000),
    is_enabled BIT,
    created_by_id INTEGER,
    modified_by_id INTEGER,
    created_date DATETIME NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    modified_date DATETIME
  );

  ALTER TABLE ${ohdsiSchema}.tool ADD CONSTRAINT PK_tool PRIMARY KEY (id);

  ALTER TABLE ${ohdsiSchema}.tool ADD CONSTRAINT fk_tool_ser_user_creator FOREIGN KEY (created_by_id) REFERENCES ${ohdsiSchema}.sec_user(id);
  ALTER TABLE ${ohdsiSchema}.tool ADD CONSTRAINT fk_tool_ser_user_updater FOREIGN KEY (modified_by_id) REFERENCES ${ohdsiSchema}.sec_user(id);
END

IF NOT EXISTS (
    SELECT 1
    FROM sys.sequences s
    WHERE s.name = 'tool_seq'
      AND s.schema_id = schema_id('${ohdsiSchema}')
)
BEGIN
  CREATE SEQUENCE ${ohdsiSchema}.tool_seq START WITH 1 INCREMENT BY 1 MAXVALUE 9223372036854775807 NO CYCLE;
END

INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'tool:post', 'Create Tool');
INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'tool:put', 'Update Tool');
INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'tool:get', 'List Tools');
INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'tool:*:get', 'View Tool');
INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'tool:*:delete', 'Delete Tool');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
    'tool:post',
    'tool:put',
    'tool:get',
    'tool:*:get',
    'tool:*:delete'
    ) AND sr.name IN ('admin');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
    'tool:get',
    'tool:*:get'
    ) AND sr.name IN ('Atlas users');
