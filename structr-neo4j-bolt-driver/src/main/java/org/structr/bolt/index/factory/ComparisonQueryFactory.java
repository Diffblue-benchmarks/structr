/**
 * Copyright (C) 2010-2018 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.bolt.index.factory;

import org.structr.api.search.ComparisonQuery;
import org.structr.api.search.QueryPredicate;
import org.structr.bolt.index.AdvancedCypherQuery;

public class ComparisonQueryFactory extends AbstractQueryFactory {
	@Override
	public boolean createQuery(final QueryFactory parent, final QueryPredicate predicate, final AdvancedCypherQuery query, final boolean isFirst) {

		if (predicate instanceof ComparisonQuery) {

			checkOccur(query, predicate.getOccurrence(), isFirst);

			final ComparisonQuery comparisonQuery			= (ComparisonQuery)predicate;
			final Object value     							= getReadValue(comparisonQuery.getSearchValue());
			final ComparisonQuery.Operation operation       = comparisonQuery.getOperation();
			final String name           					= predicate.getName();

			if (value == null && operation == null) {
				return false;
			}

			String operationString = null;
			switch (operation) {
				case equal:
					operationString = "=";
					break;
				case notEqual:
					operationString = "<>";
					break;
				case greater:
					operationString = ">";
					break;
				case greaterOrEqual:
					operationString = ">=";
					break;
				case less:
					operationString = "<";
					break;
				case lessOrEqual:
					operationString = "<=";
					break;
				case isNull:
					query.addSimpleParameter(name, "IS", null);
					return true;
				case isNotNull:
					query.addSimpleParameter(name, "IS NOT", null);
					return true;
				case startsWith:
					operationString = "STARTS WITH";
					break;
				case endsWith:
					operationString = "ENDS WITH";
					break;
				case contains:
					operationString = "CONTAINS";
					break;
				case caseInsensitiveStartsWith:
					operationString = "STARTS WITH";
					query.addSimpleParameter(name, operationString, value.toString().toLowerCase(), true, true);
					return true;
				case caseInsensitiveEndsWith:
					operationString = "ENDS WITH";
					query.addSimpleParameter(name, operationString, value.toString().toLowerCase(), true, true);
					return true;
				case caseInsensitiveContains:
					operationString = "CONTAINS";
					query.addSimpleParameter(name, operationString, value.toString().toLowerCase(), true, true);
					return true;
			}

			query.addSimpleParameter(name, operationString, value);
		}

		return true;
	}
}
