INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description)
VALUES (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:get', 'List reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:post', 'Create reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:exists:get', 'Check name uniqueness of reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:put', 'Update reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:post', 'Copy reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:get', 'Get reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:delete', 'Delete reusable');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'reusable:get',
                   'reusable:post',
                   'reusable:*:post',
                   'reusable:*:exists:get',
                   'reusable:*:get')
  AND sr.name IN ('Atlas users');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'reusable:*:put',
                   'reusable:*:delete'
    ) AND sr.name IN ('Moderator');

CREATE SEQUENCE ${ohdsiSchema}.reusable_seq;

CREATE TABLE ${ohdsiSchema}.reusable
(
    id             INT NOT NULL CONSTRAINT df_reusable_id DEFAULT NEXT VALUE FOR ${ohdsiSchema}.reusable_seq,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(1000) NULL,
    data           VARCHAR(MAX) NOT NULL,
    created_by_id  INTEGER,
    created_date   DATETIME NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    modified_by_id INTEGER,
    modified_date  DATETIME,
    CONSTRAINT pk_reusable_id PRIMARY KEY (id),
    CONSTRAINT fk_reusable_sec_user_creator FOREIGN KEY (created_by_id) REFERENCES ${ohdsiSchema}.sec_user (id),
    CONSTRAINT fk_reusable_sec_user_updater FOREIGN KEY (modified_by_id) REFERENCES ${ohdsiSchema}.sec_user (id)
);

CREATE UNIQUE INDEX reusable_name_idx ON ${ohdsiSchema}.reusable (name);

INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description)
VALUES (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:tag:post',
        'Assign tag to reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:tag:*:delete',
        'Unassign tag from reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:protectedtag:post',
        'Assign tag to reusable'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:protectedtag:*:delete',
        'Unassign tag from reusable');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'reusable:*:tag:post',
                   'reusable:*:tag:*:delete')
  AND sr.name IN ('Atlas users');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'reusable:*:protectedtag:post',
                   'reusable:*:protectedtag:*:delete')
  AND sr.name IN ('admin');

CREATE TABLE ${ohdsiSchema}.reusable_tag
(
    asset_id INT NOT NULL,
    tag_id   INT NOT NULL,
    CONSTRAINT pk_reusable_tag_id PRIMARY KEY (asset_id, tag_id),
    CONSTRAINT reusable_tag_fk_reusable FOREIGN KEY (asset_id) REFERENCES ${ohdsiSchema}.reusable (id) ON DELETE CASCADE,
    CONSTRAINT reusable_tag_fk_tag FOREIGN KEY (tag_id) REFERENCES ${ohdsiSchema}.tags (id) ON DELETE CASCADE
);

CREATE INDEX reusable_tag_reusableidx ON ${ohdsiSchema}.reusable_tag (asset_id);
CREATE INDEX reusable_tag_tag_id_idx ON ${ohdsiSchema}.reusable_tag (tag_id);

INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description)
VALUES (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:version:get',
        'Get list of reusables versions'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:version:*:get',
        'Get reusable version'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:version:*:put',
        'Update reusable version info'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:version:*:delete',
        'Delete reusable version info'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:*:version:*:createAsset:put',
        'Copy reusable version as new reusable');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'reusable:*:version:get',
                   'reusable:*:version:*:get',
                   'reusable:*:version:*:put',
                   'reusable:*:version:*:delete',
                   'reusable:*:version:*:createAsset:put')
  AND sr.name IN ('Atlas users');

CREATE TABLE ${ohdsiSchema}.reusable_version
(
    asset_id      BIGINT NOT NULL,
    comment       VARCHAR(1000) NULL,
    description   VARCHAR(1000) NULL,
    version       INT NOT NULL DEFAULT 1,
    asset_json    VARCHAR(MAX) NOT NULL,
    archived      BIT NOT NULL DEFAULT 0,
    created_by_id INTEGER,
    created_date  DATETIME NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    CONSTRAINT pk_reusable_version_id PRIMARY KEY (asset_id, version),
    CONSTRAINT fk_reusable_version_sec_user_creator FOREIGN KEY (created_by_id) REFERENCES ${ohdsiSchema}.sec_user (id),
    CONSTRAINT fk_reusable_version_asset_id FOREIGN KEY (asset_id) REFERENCES ${ohdsiSchema}.reusable (id) ON DELETE CASCADE
);

CREATE INDEX reusable_version_asset_idx ON ${ohdsiSchema}.reusable_version (asset_id);
