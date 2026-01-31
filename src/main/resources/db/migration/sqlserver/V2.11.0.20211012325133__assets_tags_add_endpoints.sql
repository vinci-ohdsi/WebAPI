INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description)
VALUES (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'cohortdefinition:byTags:post',
        'Get cohort definitions with certain assigned tags'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'conceptset:byTags:post',
        'Get concept sets with certain assigned tags'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'cohort-characterization:byTags:post',
        'Get cohort characterizations with certain assigned tags'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'ir:byTags:post',
        'Get incidence rates with certain assigned tags'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'pathway-analysis:byTags:post',
        'Get pathways with certain assigned tags'),
       (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'reusable:byTags:post',
        'Get reusables with certain assigned tags');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp,
     ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                   'cohortdefinition:byTags:post',
                   'conceptset:byTags:post',
                   'cohort-characterization:byTags:post',
                   'ir:byTags:post',
                   'pathway-analysis:byTags:post',
                   'reusable:byTags:post')
  AND sr.name IN ('Atlas users');
