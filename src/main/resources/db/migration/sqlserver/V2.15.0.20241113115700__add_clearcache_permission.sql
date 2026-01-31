INSERT INTO ${ohdsiSchema}.sec_permission (id, value, description)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq,
       'cdmresults:clearcache:post',
       'Clear the Achilles and CDM results caches';

INSERT INTO ${ohdsiSchema}.sec_role_permission (id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value in
      (
       'cdmresults:clearcache:post'
      )
  AND sr.name IN ('admin');

INSERT INTO ${ohdsiSchema}.sec_permission (id, value, description)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq,
       'cdmresults:*:clearcache:post',
       'Clear the Achilles and CDM results caches';

INSERT INTO ${ohdsiSchema}.sec_role_permission (id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value in
      (
       'cdmresults:*:clearcache:post'
      )
  AND sr.name IN ('admin');
