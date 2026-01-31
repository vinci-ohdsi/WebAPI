ALTER TABLE ${ohdsiSchema}.source
  ADD check_connection BIT NOT NULL CONSTRAINT df_source_check_connection DEFAULT (1);
