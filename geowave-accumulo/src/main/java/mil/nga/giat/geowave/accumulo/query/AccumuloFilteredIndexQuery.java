package mil.nga.giat.geowave.accumulo.query;

import java.util.Iterator;
import java.util.List;

import mil.nga.giat.geowave.accumulo.AccumuloOperations;
import mil.nga.giat.geowave.accumulo.util.CloseableIteratorWrapper;
import mil.nga.giat.geowave.accumulo.util.CloseableIteratorWrapper.ScannerClosableWrapper;
import mil.nga.giat.geowave.accumulo.util.EntryIteratorWrapper;
import mil.nga.giat.geowave.accumulo.util.EntryWithKeyIteratorWrapper;
import mil.nga.giat.geowave.index.ByteArrayId;
import mil.nga.giat.geowave.index.StringUtils;
import mil.nga.giat.geowave.store.CloseableIterator;
import mil.nga.giat.geowave.store.adapter.AdapterStore;
import mil.nga.giat.geowave.store.filter.FilterList;
import mil.nga.giat.geowave.store.filter.QueryFilter;
import mil.nga.giat.geowave.store.index.Index;

import org.apache.accumulo.core.client.ScannerBase;
import org.apache.log4j.Logger;

import com.google.common.collect.Iterators;

public abstract class AccumuloFilteredIndexQuery extends
		AccumuloQuery
{
	protected List<QueryFilter> clientFilters;
	private final static Logger LOGGER = Logger.getLogger(AccumuloFilteredIndexQuery.class);

	public AccumuloFilteredIndexQuery(
			final Index index,
			final List<QueryFilter> clientFilters,
			final String... authorizations ) {
		super(
				index,
				authorizations);
		this.clientFilters = clientFilters;
	}

	public AccumuloFilteredIndexQuery(
			final List<ByteArrayId> adapterIds,
			final Index index,
			final List<QueryFilter> clientFilters,
			final String... authorizations ) {
		super(
				adapterIds,
				index,
				authorizations);
		this.clientFilters = clientFilters;
	}

	public AccumuloFilteredIndexQuery(
			final Index index,
			final String... authorizations ) {
		super(
				index,
				authorizations);
	}

	public AccumuloFilteredIndexQuery(
			final List<ByteArrayId> adapterIds,
			final Index index,
			final String... authorizations ) {
		super(
				adapterIds,
				index,
				authorizations);
	}

	protected List<QueryFilter> getClientFilters() {
		return clientFilters;
	}

	protected void setClientFilters(
			final List<QueryFilter> clientFilters ) {
		this.clientFilters = clientFilters;
	}

	protected abstract void addScanIteratorSettings(
			final ScannerBase scanner );

	public CloseableIterator<?> query(
			final AccumuloOperations accumuloOperations,
			final AdapterStore adapterStore,
			final Integer limit ) {
		return query(
				accumuloOperations,
				adapterStore,
				limit,
				false);
	}

	@SuppressWarnings("rawtypes")
	public CloseableIterator<?> query(
			final AccumuloOperations accumuloOperations,
			final AdapterStore adapterStore,
			final Integer limit,
			final boolean withKeys ) {
		if (!accumuloOperations.tableExists(StringUtils.stringFromBinary(index.getId().getBytes()))) {
			LOGGER.warn("Table does not exist " + StringUtils.stringFromBinary(index.getId().getBytes()));
			return new CloseableIterator.Empty();
		}
		final ScannerBase scanner = getScanner(
				accumuloOperations,
				limit);
		addScanIteratorSettings(scanner);
		Iterator it = initIterator(
				scanner,
				adapterStore,
				withKeys);
		if ((limit != null) && (limit > 0)) {
			it = Iterators.limit(
					it,
					limit);
		}
		return new CloseableIteratorWrapper(
				new ScannerClosableWrapper(
						scanner),
				it);
	}

	protected Iterator initIterator(
			final ScannerBase scanner,
			final AdapterStore adapterStore,
			final boolean withKeys ) {
		if (withKeys) {
			return new EntryWithKeyIteratorWrapper(
					adapterStore,
					index,
					scanner.iterator(),
					new FilterList<QueryFilter>(
							clientFilters));
		}
		return new EntryIteratorWrapper(
				adapterStore,
				index,
				scanner.iterator(),
				new FilterList<QueryFilter>(
						clientFilters));
	}

}
