CREATE TABLE #temp_migration (
  from_perm_id INT,
  new_value VARCHAR(255)
);

INSERT INTO #temp_migration (from_perm_id, new_value)
SELECT sp.id as from_id,
  REPLACE('vocabulary:%s:lookup:recommended:post', '%s', REPLACE(REPLACE(sp.value, 'source:', ''), ':access', '')) as new_value
FROM ${ohdsiSchema}.sec_permission sp
WHERE sp.value LIKE 'source:%:access';

INSERT INTO ${ohdsiSchema}.sec_permission (id, value)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, new_value
FROM #temp_migration;

INSERT INTO ${ohdsiSchema}.sec_role_permission (id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence,
  srp.role_id,
  sp.id as permission_id
FROM #temp_migration m
JOIN ${ohdsiSchema}.sec_permission sp on m.new_value = sp.value
JOIN ${ohdsiSchema}.sec_role_permission srp on m.from_perm_id = srp.permission_id;

DROP TABLE #temp_migration;
