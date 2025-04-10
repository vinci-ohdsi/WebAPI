/*
 * Copyright 2015 Observational Health Data Sciences and Informatics <OHDSI.org>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ohdsi.webapi.cohortdefinition;

import org.ohdsi.circe.helper.ResourceHelper;
import org.ohdsi.sql.SqlCteRefactor;
import org.ohdsi.sql.SqlRender;
import org.ohdsi.sql.SqlSplit;
import org.ohdsi.sql.SqlTranslate;
import org.ohdsi.webapi.common.generation.CancelableTasklet;
import org.ohdsi.webapi.generationcache.GenerationCacheHelper;
import org.ohdsi.webapi.source.Source;
import org.ohdsi.webapi.source.SourceService;
import org.ohdsi.webapi.util.CancelableJdbcTemplate;
import org.ohdsi.webapi.util.SessionUtils;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.ohdsi.webapi.Constants.Params.*;

/**
 *
 * @author Chris Knoll <cknoll@ohdsi.org>
 */
public class GenerateCohortTasklet extends CancelableTasklet implements StoppableTasklet {
  private final static String copyGenerationIntoCohortTableSql = ResourceHelper.GetResourceAsString("/resources/cohortdefinition/sql/copyGenerationIntoCohortTableSql.sql");

  private final GenerationCacheHelper generationCacheHelper;
  private final CohortDefinitionRepository cohortDefinitionRepository;
  private final SourceService sourceService;

  public GenerateCohortTasklet(
          final CancelableJdbcTemplate jdbcTemplate,
          final TransactionTemplate transactionTemplate,
          final GenerationCacheHelper generationCacheHelper,
          final CohortDefinitionRepository cohortDefinitionRepository,
          final SourceService sourceService
  ) {
    super(LoggerFactory.getLogger(GenerateCohortTasklet.class), jdbcTemplate, transactionTemplate);
    this.generationCacheHelper = generationCacheHelper;
    this.cohortDefinitionRepository = cohortDefinitionRepository;
    this.sourceService = sourceService;
  }

  @Override
  protected String[] prepareQueries(ChunkContext chunkContext, CancelableJdbcTemplate jdbcTemplate) {

    Map<String, Object> jobParams = chunkContext.getStepContext().getJobParameters();

    Integer cohortDefinitionId = Integer.valueOf(jobParams.get(COHORT_DEFINITION_ID).toString());
    Integer sourceId = Integer.parseInt(jobParams.get(SOURCE_ID).toString());
    String targetSchema = jobParams.get(TARGET_DATABASE_SCHEMA).toString();
    String sessionId = jobParams.getOrDefault(SESSION_ID, SessionUtils.sessionId()).toString();

    CohortDefinition cohortDefinition = cohortDefinitionRepository.findOneWithDetail(cohortDefinitionId);
    Source source = sourceService.findBySourceId(sourceId);

    CohortGenerationRequestBuilder generationRequestBuilder = new CohortGenerationRequestBuilder(
        sessionId,
        targetSchema
    );

    int designHash = this.generationCacheHelper.computeHash(cohortDefinition.getDetails().getExpression());
    CohortGenerationUtils.insertInclusionRules(cohortDefinition, source, designHash, targetSchema, sessionId, jdbcTemplate);
    
    GenerationCacheHelper.CacheResult res = generationCacheHelper.computeCacheIfAbsent(
        cohortDefinition,
        source,
        generationRequestBuilder,
        (resId, sqls) -> generationCacheHelper.runCancelableCohortGeneration(jdbcTemplate, stmtCancel, sqls)
    );

    String sql = SqlRender.renderSql(
        copyGenerationIntoCohortTableSql,
        new String[]{ RESULTS_DATABASE_SCHEMA, COHORT_DEFINITION_ID, DESIGN_HASH },
        new String[]{ targetSchema, cohortDefinition.getId().toString(), res.getIdentifier().toString() }
    );
    sql = SqlTranslate.translateSql(sql, source.getSourceDialect());
		if (DO_REFACTOR) {
		        this.log.info("GenerateCohortTasklet::generationRequestBuilder calling translateToCustomVaSql");
			sql = SqlCteRefactor.translateToCustomVaSql(sql);
			this.log.info("GenerateCohortTasklet::generationRequestBuilder translateToCustomVaSql returned. New SQL:\n\n" + sql + "\n\n");	      
		}
    return SqlSplit.splitSql(sql);
  }
	
	private static final boolean DO_REFACTOR = true;

//	private static final boolean DO_REFACTOR =
//		Boolean.getBoolean(getEnvironmentalVariableWithDefault(REFACTOR_VARIABLE_NAME, "true"));
//
//	private static String getEnvironmentalVariableWithDefault(String variableName, String defaultValue) {
//		String value = System.getenv(variableName);
//		return (value != null) ? value : defaultValue;
//	}		
//		
//	private static final String REFACTOR_VARIABLE_NAME = "do.sql.refactor";
}
