package org.ohdsi.sql;

import java.lang.reflect.Constructor;

/**
 * @author Marc A Suchard
 * @author Richard Boyce
 */

@SuppressWarnings("unused")
public class SystemPropertiesSqlRefactorConfig extends RSqlRefactorConfig {
	
	private static final String REFACTOR_DOMAIN_CRITERIA = "refactor.domain.criteria";
	private static final String REFACTOR_NESTED_CRITERIA = "refactor.nested.criteria";
	private static final String REFACTOR_PRIMARY_EVENTS = "refactor.primary.events";
	private static final String ADD_INDICES_TO_DOMAIN_CRITERIA = "add.indices.to.domain.criteria";
	private static final String ADD_INDICES_TO_NESTED_CRITERIA = "add.indices.to.nested.criteria";

	public SystemPropertiesSqlRefactorConfig() {
		super(
			getProperty(REFACTOR_PRIMARY_EVENTS, true),
			getProperty(REFACTOR_NESTED_CRITERIA, true),
			getProperty(REFACTOR_PRIMARY_EVENTS, true),
			getProperty(ADD_INDICES_TO_DOMAIN_CRITERIA, false),
			getProperty(ADD_INDICES_TO_NESTED_CRITERIA, false));
	}

	@SuppressWarnings("unchecked")
	private static <T> T getProperty(String tag, T defaultValue) {
		String s = System.getProperty(tag);
		if (s != null && !s.isEmpty()) {
			for (Constructor<?> c : defaultValue.getClass().getConstructors()) {
				final Class<?>[] classes = c.getParameterTypes();
				if (classes.length == 1 && classes[0].equals(String.class)) {
					try {
						return (T) c.newInstance(s);
					} catch (Exception e) {
						throw new RuntimeException("conversion of '" + s + "' to " +
							defaultValue.getClass().getName() + " failed");
					}
				}
			}
		}
		return defaultValue;
	}
}
