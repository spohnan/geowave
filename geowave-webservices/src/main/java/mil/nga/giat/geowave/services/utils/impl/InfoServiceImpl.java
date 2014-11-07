package mil.nga.giat.geowave.services.utils.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.container.AsyncResponse;

import mil.nga.giat.geowave.services.utils.InfoService;
import mil.nga.giat.geowave.services.utils.data.AdapterInfo;
import mil.nga.giat.geowave.services.utils.data.IndexInfo;
import mil.nga.giat.geowave.services.utils.data.NamespaceInfo;

public class InfoServiceImpl implements
		InfoService
{

	private ExecutorService executor;

	public InfoServiceImpl() {
		super();
		executor = Executors.newFixedThreadPool(8);
	}

	@Override
	public void getNamespaces(
			final AsyncResponse asyncResponse ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final String[] result = getNamespaces();
				asyncResponse.resume(result);
			}
		});
	}

	private String[] getNamespaces() {
		return null;
	}

	@Override
	public void getNamespaceInfo(
			final AsyncResponse asyncResponse,
			final String namespace ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final NamespaceInfo result = getNamespaceInfo(namespace);
				asyncResponse.resume(result);
			}
		});
	}

	private NamespaceInfo getNamespaceInfo(
			final String namespace ) {
		return null;
	}

	@Override
	public void getIndices(
			final AsyncResponse asyncResponse,
			final String namespace ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final String[] result = getIndices(namespace);
				asyncResponse.resume(result);
			}
		});
	}

	private String[] getIndices(
			final String namespace ) {
		return null;
	}

	@Override
	public void getIndexInfo(
			final AsyncResponse asyncResponse,
			final String namespace,
			final String indexid ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final IndexInfo result = getIndexInfo(
						namespace,
						indexid);
				asyncResponse.resume(result);
			}
		});
	}

	private IndexInfo getIndexInfo(
			final String namespace,
			final String indexid ) {
		return null;
	}

	@Override
	public void getAdapters(
			final AsyncResponse asyncResponse,
			final String namespace,
			final String indexid ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final String[] result = getAdapters(
						namespace,
						indexid);
				asyncResponse.resume(result);
			}

		});
	}

	private String[] getAdapters(
			final String namespace,
			final String indexid ) {
		return null;
	}

	@Override
	public void getAdapterInfo(
			final AsyncResponse asyncResponse,
			final String namespace,
			final String adapterid,
			final String indexid ) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				final AdapterInfo result = getAdapterInfo(
						namespace,
						adapterid,
						indexid);
				asyncResponse.resume(result);
			}
		});
	}

	private AdapterInfo getAdapterInfo(
			final String namespace,
			final String adapterid,
			final String indexid ) {
		return null;
	}

}
