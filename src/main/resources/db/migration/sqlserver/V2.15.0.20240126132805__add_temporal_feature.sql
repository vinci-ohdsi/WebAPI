INSERT INTO ${ohdsiSchema}.sec_permission(id, value, description) VALUES
    (NEXT VALUE FOR ${ohdsiSchema}.sec_permission_id_seq, 'cohort-characterization:generation:*:temporalresult:get', 'Get cohort characterization generation temporal results');

INSERT INTO ${ohdsiSchema}.sec_role_permission(id, role_id, permission_id)
SELECT NEXT VALUE FOR ${ohdsiSchema}.sec_role_permission_sequence, sr.id, sp.id
FROM ${ohdsiSchema}.sec_permission sp, ${ohdsiSchema}.sec_role sr
WHERE sp.value IN (
                     'cohort-characterization:generation:*:temporalresult:get'
    )
  AND sr.name IN ('Atlas users');

IF COL_LENGTH('${ohdsiSchema}.fe_analysis', 'supports_annual') IS NULL
  ALTER TABLE ${ohdsiSchema}.fe_analysis
    ADD supports_annual BIT CONSTRAINT df_fe_analysis_supports_annual DEFAULT (0);

IF COL_LENGTH('${ohdsiSchema}.fe_analysis', 'supports_temporal') IS NULL
  ALTER TABLE ${ohdsiSchema}.fe_analysis
    ADD supports_temporal BIT CONSTRAINT df_fe_analysis_supports_temporal DEFAULT (0);

UPDATE ${ohdsiSchema}.fe_analysis
    SET supports_annual = 1
WHERE design in (
    'ConditionOccurrenceAnyTimePrior',
    'ConditionOccurrenceLongTerm',
    'ConditionOccurrenceMediumTerm',
    'ConditionOccurrenceShortTerm',
    'ConditionOccurrencePrimaryInpatientAnyTimePrior',
    'ConditionOccurrencePrimaryInpatientLongTerm',
    'ConditionOccurrencePrimaryInpatientMediumTerm',
    'ConditionOccurrencePrimaryInpatientShortTerm',
    'ConditionEraAnyTimePrior',
    'ConditionEraLongTerm',
    'ConditionEraMediumTerm',
    'ConditionEraShortTerm',
    'ConditionEraOverlapping',
    'ConditionEraStartLongTerm',
    'ConditionEraStartMediumTerm',
    'ConditionEraStartShortTerm',
    'DrugExposureAnyTimePrior',
    'DrugExposureLongTerm',
    'DrugExposureMediumTerm',
    'DrugExposureShortTerm',
    'DrugEraAnyTimePrior',
    'DrugEraLongTerm',
    'DrugEraMediumTerm',
    'DrugEraShortTerm',
    'DrugEraOverlapping',
    'DrugEraStartLongTerm',
    'DrugEraStartMediumTerm',
    'DrugEraStartShortTerm',
    'ProcedureOccurrenceAnyTimePrior',
    'ProcedureOccurrenceLongTerm',
    'ProcedureOccurrenceMediumTerm',
    'ProcedureOccurrenceShortTerm',
    'DeviceExposureAnyTimePrior',
    'DeviceExposureLongTerm',
    'DeviceExposureMediumTerm',
    'DeviceExposureShortTerm',
    'MeasurementAnyTimePrior',
    'MeasurementLongTerm',
    'MeasurementMediumTerm',
    'MeasurementShortTermObservationAnyTimePrior',
    'ObservationLongTerm',
    'ObservationMediumTerm',
    'ObservationShortTerm'
);

UPDATE ${ohdsiSchema}.fe_analysis
    SET supports_temporal = 1
WHERE
    design in (
        'DemographicsGender',
        'DemographicsAge',
        'DemographicsAgeGroup',
        'DemographicsRace',
        'DemographicsEthnicity',
        'DemographicsIndexYear',
        'DemographicsIndexMonth',
        'DemographicsPriorObservationTime',
        'DemographicsPostObservationTime',
        'DemographicsTimeInCohort',
        'DemographicsIndexYearMonth',
        'CareSiteId',
        'ConditionOccurrence',
        'ConditionOccurrencePrimaryInpatient',
        'ConditionEraStart',
        'ConditionEraOverlap',
        'ConditionEraGroupStart',
        'ConditionEraGroupOverlap',
        'DrugExposure',
        'DrugEraStart',
        'DrugEraOverlap',
        'DrugEraGroupStart',
        'DrugEraGroupOverlap',
        'ProcedureOccurrence',
        'DeviceExposure',
        'Measurement',
        'MeasurementValue',
        'MeasurementRangeGroup',
        'MeasurementValueAsConcept',
        'Observation',
        'ObservationValueAsConcept',
        'CharlsonIndex',
        'Dcsi',
        'Chads2',
        'Chads2Vasc',
        'Hfrs',
        'DistinctConditionCount',
        'DistinctIngredientCount',
        'DistinctProcedureCount',
        'DistinctMeasurementCount',
        'DistinctObservationCount',
        'VisitCount',
        'VisitConceptCount'
);

IF NOT EXISTS (
    SELECT 1
    FROM sys.sequences s
    WHERE s.name = 'cc_analysis_seq'
      AND s.schema_id = schema_id('${ohdsiSchema}')
)
BEGIN
  CREATE SEQUENCE ${ohdsiSchema}.cc_analysis_seq;
END

IF COL_LENGTH('${ohdsiSchema}.cc_analysis', 'id') IS NULL
BEGIN
  ALTER TABLE ${ohdsiSchema}.cc_analysis
    ADD id BIGINT NULL;

  UPDATE ${ohdsiSchema}.cc_analysis
    SET id = NEXT VALUE FOR ${ohdsiSchema}.cc_analysis_seq;

  ALTER TABLE ${ohdsiSchema}.cc_analysis
    ALTER COLUMN id BIGINT NOT NULL;

  ALTER TABLE ${ohdsiSchema}.cc_analysis
    ADD CONSTRAINT df_cc_analysis_id DEFAULT (NEXT VALUE FOR ${ohdsiSchema}.cc_analysis_seq) FOR id;
END

IF COL_LENGTH('${ohdsiSchema}.cc_analysis', 'include_annual') IS NULL
  ALTER TABLE ${ohdsiSchema}.cc_analysis
    ADD include_annual BIT CONSTRAINT df_cc_analysis_include_annual DEFAULT (0);

IF COL_LENGTH('${ohdsiSchema}.cc_analysis', 'include_temporal') IS NULL
  ALTER TABLE ${ohdsiSchema}.cc_analysis
    ADD include_temporal BIT CONSTRAINT df_cc_analysis_include_temporal DEFAULT (0);

DECLARE @cc_analysis_next BIGINT;
DECLARE @cc_analysis_sql NVARCHAR(MAX);

SELECT @cc_analysis_next = ISNULL(MAX(id), 0) + 1 FROM ${ohdsiSchema}.cc_analysis;
SET @cc_analysis_sql = N'ALTER SEQUENCE ${ohdsiSchema}.cc_analysis_seq RESTART WITH ' + CAST(@cc_analysis_next as NVARCHAR(20)) + ';';
EXEC sp_executesql @cc_analysis_sql;

DECLARE @pk NVARCHAR(128);
SELECT @pk = kc.name
FROM sys.key_constraints kc
JOIN sys.tables t ON kc.parent_object_id = t.object_id
WHERE t.name = 'cc_analysis'
  AND t.schema_id = schema_id('${ohdsiSchema}')
  AND kc.type = 'PK';

IF @pk IS NOT NULL
  EXEC('ALTER TABLE ${ohdsiSchema}.cc_analysis DROP CONSTRAINT ' + @pk);

ALTER TABLE ${ohdsiSchema}.cc_analysis
    ADD CONSTRAINT cc_analysis_pkey PRIMARY KEY (id);
