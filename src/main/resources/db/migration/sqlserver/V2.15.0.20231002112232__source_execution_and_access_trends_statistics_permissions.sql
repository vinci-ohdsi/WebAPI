INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'statistic:executions:post', 'Source execution statistics permission');

INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'statistic:accesstrends:post', 'Access trends statistics permission');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN ('statistic:executions:post') AND sr.name IN ('admin');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN ('statistic:accesstrends:post') AND sr.name IN ('admin');
