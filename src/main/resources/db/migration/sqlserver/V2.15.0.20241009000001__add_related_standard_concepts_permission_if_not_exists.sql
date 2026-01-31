INSERT INTO  ${ohdsiSchema}.sec_permission (id, value, description)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'vocabulary:*:related-standard:post', 'Access related mapped standard concepts resource'
WHERE NOT EXISTS (
        SELECT 1 FROM  ${ohdsiSchema}.sec_permission
        WHERE value = 'vocabulary:*:related-standard:post'
);

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
    'vocabulary:*:related-standard:post'
    ) AND sr.name IN ('Atlas users')
  AND NOT EXISTS (
        SELECT 1 FROM ${ohdsiSchema}.sec_role_permission
        WHERE permission_id = sp.id and role_id = sr.id);
