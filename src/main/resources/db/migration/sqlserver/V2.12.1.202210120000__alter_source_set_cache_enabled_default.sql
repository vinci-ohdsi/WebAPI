DECLARE @command NVARCHAR(1000);

SELECT @command = 'ALTER TABLE ${ohdsiSchema}.source DROP CONSTRAINT ' + d.name
FROM sys.tables t
  JOIN sys.default_constraints d ON d.parent_object_id = t.object_id
  JOIN sys.columns c ON c.object_id = t.object_id AND c.column_id = d.parent_column_id
WHERE t.name = 'source'
  AND t.schema_id = schema_id('${ohdsiSchema}')
  AND c.name = 'is_cache_enabled';

IF @command IS NOT NULL
  EXEC (@command);

ALTER TABLE ${ohdsiSchema}.source
  ADD CONSTRAINT df_source_is_cache_enabled DEFAULT (0) FOR is_cache_enabled;
