package org.ohdsi.webapi.service;

import static org.ohdsi.webapi.Constants.DEFAULT_DIALECT;
import static org.ohdsi.webapi.Constants.CTE_REFACTOR;

import static org.ohdsi.webapi.Constants.SqlSchemaPlaceholders.TEMP_DATABASE_SCHEMA_PLACEHOLDER;

import java.util.Collections;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.ohdsi.sql.SqlCteRefactor;
import org.ohdsi.sql.SqlRender;
import org.ohdsi.sql.SqlTranslate;
import org.ohdsi.webapi.sqlrender.SourceStatement;
import org.ohdsi.webapi.sqlrender.TranslatedStatement;
import org.ohdsi.webapi.util.SessionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Lee Evans
 */
@Path("/sqlrender/")
public class SqlRenderService {
    // Create a logger instance for this classAdd commentMore actions
    private static final Logger logger = LoggerFactory.getLogger(SqlRenderService.class);
     
    /**
     * Translate an OHDSI SQL to a supported target SQL dialect
     * @param sourceStatement JSON with parameters, source SQL, and target dialect
     * @return rendered and translated SQL
     */
    @Path("translate")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public TranslatedStatement translateSQLFromSourceStatement(SourceStatement sourceStatement) {
	logger.info("SqlRenderService::translateSQLFromSourceStatement entered ");
        if (sourceStatement == null) {
	    logger.info("SqlRenderService::translateSQLFromSourceStatement sourceStatement is null, returning an empty TranslatedStatement");
            return new TranslatedStatement();
        }
        sourceStatement.setOracleTempSchema(TEMP_DATABASE_SCHEMA_PLACEHOLDER);

	logger.info("SqlRenderService::translateSQLFromSourceStatement calling translatedStatement ");
        return translatedStatement(sourceStatement);
    }

    public TranslatedStatement translatedStatement(SourceStatement sourceStatement) {
	logger.info("SqlRenderService::translatedStatement calling translateSQL");
        return translateSQL(sourceStatement);
    }


    public static TranslatedStatement translateSQL(SourceStatement sourceStatement) {
	logger.info("SqlRenderService::translateSql entered");
        TranslatedStatement translated = new TranslatedStatement();
        if (sourceStatement == null) {
	    logger.info("SqlRenderService::translateSql sourceStatement is null, returning an empty TranslatedStatement");
            return translated;
        }

        try {
            Map<String, String> parameters = sourceStatement.getParameters() == null ? Collections.emptyMap() : sourceStatement.getParameters();

            String renderedSQL = SqlRender.renderSql(
                    sourceStatement.getSql(),
                    parameters.keySet().toArray(new String[0]),
                    parameters.values().toArray(new String[0]));
	    
	    logger.info("SqlRenderService::translateSql renderedSQL completed. SQL: " + renderedSQL);
            translated.setTargetSQL(translateSql( sourceStatement, renderedSQL));

            return translated;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

    }

    private static String translateSql(SourceStatement sourceStatement, String renderedSQL) {
        if (StringUtils.isEmpty(sourceStatement.getTargetDialect()) && !DEFAULT_DIALECT.equals(sourceStatement.getTargetDialect())) {
	  logger.info("SqlRenderService::translateSql returning renderedSQL directly without CTE translation because sourceStatement.getTargetDialect() is empty or is not equal to the DEFAULT_DIALECT");
            return renderedSQL;
        }
	
	String sql = SqlTranslate.translateSql(renderedSQL, sourceStatement.getTargetDialect(), SessionUtils.sessionId(), sourceStatement.getOracleTempSchema());

	if(DEFAULT_DIALECT.equals(sourceStatement.getTargetDialect())){ // implies "sql server" is the dialect
	  if (CTE_REFACTOR.equals("true")) {
		logger.info("SqlRenderService::translateSql calling translateToCustomVaSql");
		sql = SqlCteRefactor.translateToCustomVaSql(sql);
		logger.info("SqlRenderService::translateSql translateToCustomVaSql returned. New SQL:\n\n" + sql + "\n\n");
	    }
	}
        return sql;
    }
}
