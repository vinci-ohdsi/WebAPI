ALTER TABLE ${ohdsiSchema}.cohort_generation_info ADD is_demographic BIT NOT NULL CONSTRAINT df_cohort_generation_info_is_demographic DEFAULT (0);
ALTER TABLE ${ohdsiSchema}.cohort_generation_info ADD cc_generate_id INT NULL;
