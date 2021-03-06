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
package org.structr.bolt.index;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.index.Index;
import org.structr.api.search.*;
import org.structr.bolt.BoltDatabaseService;
import org.structr.bolt.index.converter.BooleanTypeConverter;
import org.structr.bolt.index.converter.ByteTypeConverter;
import org.structr.bolt.index.converter.DateTypeConverter;
import org.structr.bolt.index.converter.DoubleTypeConverter;
import org.structr.bolt.index.converter.FloatTypeConverter;
import org.structr.bolt.index.converter.IntTypeConverter;
import org.structr.bolt.index.converter.LongTypeConverter;
import org.structr.bolt.index.converter.ShortTypeConverter;
import org.structr.bolt.index.converter.StringTypeConverter;
import org.structr.bolt.index.factory.*;

/**
 *
 */
public abstract class AbstractCypherIndex<T extends PropertyContainer> implements Index<T>, QueryFactory {

	private static final Logger logger                       = LoggerFactory.getLogger(AbstractCypherIndex.class.getName());
	public static final TypeConverter DEFAULT_CONVERTER      = new StringTypeConverter();
	public static final Map<Class, TypeConverter> CONVERTERS = new HashMap<>();
	public static final Map<Class, QueryFactory> FACTORIES   = new HashMap<>();

	public static final Set<Class> INDEXABLE = new HashSet<>(Arrays.asList(new Class[] {
		String.class,   Boolean.class,   Short.class,   Integer.class,   Long.class,   Character.class,   Float.class,   Double.class,   byte.class,
		String[].class, Boolean[].class, Short[].class, Integer[].class, Long[].class, Character[].class, Float[].class, Double[].class, byte[].class
	}));

	static {

		FACTORIES.put(NotEmptyQuery.class,     new NotEmptyQueryFactory());
		FACTORIES.put(FulltextQuery.class,     new KeywordQueryFactory());
		FACTORIES.put(SpatialQuery.class,      new SpatialQueryFactory());
		FACTORIES.put(GroupQuery.class,        new GroupQueryFactory());
		FACTORIES.put(RangeQuery.class,        new RangeQueryFactory());
		FACTORIES.put(ExactQuery.class,        new KeywordQueryFactory());
		FACTORIES.put(ArrayQuery.class,        new ArrayQueryFactory());
		FACTORIES.put(EmptyQuery.class,        new EmptyQueryFactory());
		FACTORIES.put(TypeQuery.class,         new TypeQueryFactory());
		FACTORIES.put(UuidQuery.class,         new UuidQueryFactory());
		FACTORIES.put(RelationshipQuery.class, new RelationshipQueryFactory());
		FACTORIES.put(ComparisonQuery.class,   new ComparisonQueryFactory());

		CONVERTERS.put(Boolean.class, new BooleanTypeConverter());
		CONVERTERS.put(String.class,  new StringTypeConverter());
		CONVERTERS.put(Date.class,    new DateTypeConverter());
		CONVERTERS.put(Long.class,    new LongTypeConverter());
		CONVERTERS.put(Short.class,   new ShortTypeConverter());
		CONVERTERS.put(Integer.class, new IntTypeConverter());
		CONVERTERS.put(Float.class,   new FloatTypeConverter());
		CONVERTERS.put(Double.class,  new DoubleTypeConverter());
		CONVERTERS.put(byte.class,    new ByteTypeConverter());
	}

	protected final BoltDatabaseService db;

	public AbstractCypherIndex(final BoltDatabaseService db) {
		this.db = db;
	}

	public abstract Iterable<T> getResult(final PageableQuery query);
	public abstract String getQueryPrefix(final String mainType, final String sourceTypeLabel, final String targetTypeLabel);
	public abstract String getQuerySuffix(final PageableQuery query);

	@Override
	public Iterable<T> query(final QueryContext context, final QueryPredicate predicate) {

		final AdvancedCypherQuery query = new AdvancedCypherQuery(context, this);

		createQuery(this, predicate, query, true);

		final String sortKey = predicate.getSortKey();
		if (sortKey != null) {

			query.sort(predicate.getSortType(), sortKey, predicate.sortDescending());
		}

		return getResult(query);
	}

	// ----- interface QueryFactory -----
	@Override
	public boolean createQuery(final QueryFactory parent, final QueryPredicate predicate, final AdvancedCypherQuery query, final boolean isFirst) {

		final Class type = predicate.getQueryType();
		if (type != null) {

			final QueryFactory factory = FACTORIES.get(type);
			if (factory != null) {

				return factory.createQuery(this, predicate, query, isFirst);

			} else {

				logger.warn("No query factory registered for type {}", type);
			}
		}

		return false;
	}
}
