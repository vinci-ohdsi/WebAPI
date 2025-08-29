package org.ohdsi.sql;

/**
 * @author Marc A Suchard
 * @author Richard Boyce
 */

public class RSqlRefactorConfig  extends SqlRefactorConfig.Base {
	
	private final boolean refactorDomainCriteria;
	private final boolean refactorNestedCriteria;
	private final boolean refactorPrimaryEvents;
	private final boolean addIndicesToDomainCriteria;
	private final boolean addIndicesToNestedCriteria;
	
	public RSqlRefactorConfig() {
		this.refactorDomainCriteria = true;
		this.refactorNestedCriteria = true;
		this.refactorPrimaryEvents = true;
		this.addIndicesToDomainCriteria = true;
		this.addIndicesToNestedCriteria = false;
	}
	
	public RSqlRefactorConfig(boolean refactorDomainCriteria,
														boolean refactorNestedCriteria,
														boolean refactorPrimaryEvents,
														boolean addIndicesToDomainCriteria,
														boolean addIndicesToNestedCriteria) {
		this.refactorDomainCriteria = refactorDomainCriteria;
		this.refactorNestedCriteria = refactorNestedCriteria;
		this.refactorPrimaryEvents = refactorPrimaryEvents;
		this.addIndicesToDomainCriteria = addIndicesToDomainCriteria;
		this.addIndicesToNestedCriteria = addIndicesToNestedCriteria;
	}
	
	@Override
	public boolean getRefactorDomainCriteria() { return refactorDomainCriteria; }

	@Override
	public boolean getRefactorNestedCriteria() { return refactorNestedCriteria; }

	@Override
	public boolean getRefactorPrimaryEvents() { return refactorPrimaryEvents; }

	@Override
	public boolean getAddIndicesToDomainCriteria() { return addIndicesToDomainCriteria; }

	@Override
	public boolean getAddIndicesToNestedCriteria() { return addIndicesToNestedCriteria; }	
}
