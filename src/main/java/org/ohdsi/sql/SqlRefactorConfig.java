package org.ohdsi.sql;

/**
 * @author Marc A Suchard
 * @author Richard Boyce
 */

public interface SqlRefactorConfig {

	boolean getRefactorDomainCriteria();

	boolean getRefactorNestedCriteria();

	boolean getRefactorPrimaryEvents();

	boolean getAddIndicesToDomainCriteria();

	boolean getAddIndicesToNestedCriteria();
	
	abstract class Base implements SqlRefactorConfig {

		@Override
		public String toString () {
			return 
				"refactorDomainCriteria: " + getRefactorDomainCriteria() + "\n" +
				"refactorNestedCriteria: " + getRefactorNestedCriteria() + "\n" +
				"refactorPrimaryEvents:  " + getRefactorPrimaryEvents() + "\n" +
				"addIndicesToDomainCriteria: " + getAddIndicesToDomainCriteria() + "\n" +
				"addIndicesToNestedCriteria: " + getAddIndicesToNestedCriteria() + "\n";
		}
	}
}
