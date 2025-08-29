package org.ohdsi.sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

/**
 * @author Marc A Suchard
 * @author Richard Boyce
 */

@Configuration
public class SpringSqlRefactorConfig extends SqlRefactorConfig.Base {

	@Value("${vinci.refactorDomainCriteria:true}")	
	boolean refactorDomainCriteria;

	@Value("${vinci.refactorNestedCriteria}:true")
	boolean refactorNestedCriteria;

	@Value("${vinci.refactorPrimaryEvents}:true")
	boolean refactorPrimaryEvents;

	@Value("${vinci.addIndicesToDomainCriteria}:false")
	boolean addIndicesToDomainCriteria;

	@Value("${vinci.addIndicesToNestedCriteria}:false")
	boolean addIndicesToNestedCriteria;
	
	public SpringSqlRefactorConfig() {
		// Do nothing
	}

	@Override
	public boolean getRefactorDomainCriteria() { return refactorDomainCriteria;}

	@Override
	public boolean getRefactorNestedCriteria() { return refactorNestedCriteria; }

	@Override
	public boolean getRefactorPrimaryEvents() { return refactorPrimaryEvents; }

	@Override
	public boolean getAddIndicesToDomainCriteria() { return addIndicesToDomainCriteria; }

	@Override
	public boolean getAddIndicesToNestedCriteria() { return addIndicesToNestedCriteria; }

	@Bean
	public SqlRefactorConfig myService() {
		return new SpringSqlRefactorConfig();
	}	
	
	public static void main(String[] args) {
		SqlRefactorConfig config = new SpringSqlRefactorConfig();
		System.out.printf(config.toString());
	}
}
