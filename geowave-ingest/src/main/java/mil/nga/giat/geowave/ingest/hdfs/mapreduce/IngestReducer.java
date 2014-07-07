package mil.nga.giat.geowave.ingest.hdfs.mapreduce;

import java.io.IOException;

import mil.nga.giat.geowave.index.ByteArrayId;
import mil.nga.giat.geowave.index.ByteArrayUtils;
import mil.nga.giat.geowave.index.PersistenceUtils;
import mil.nga.giat.geowave.ingest.GeoWaveData;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Reducer;

/**
 * This is the map-reduce reducer for ingestion with both the mapper and
 * reducer.
 */
public class IngestReducer extends
		Reducer<WritableComparable<?>, Writable, GeoWaveIngestKey, Object>
{
	private IngestWithReducer ingestWithReducer;
	private String globalVisibility;
	private ByteArrayId primaryIndexId;

	@Override
	protected void reduce(
			final WritableComparable<?> key,
			final Iterable<Writable> values,
			final Context context )
			throws IOException,
			InterruptedException {
		final Iterable<GeoWaveData> data = ingestWithReducer.toGeoWaveData(
				key,
				primaryIndexId,
				globalVisibility,
				values);
		for (final GeoWaveData d : data) {
			context.write(
					d.getKey(),
					d.getValue());
		}
	}

	@Override
	protected void setup(
			final Context context )
			throws IOException,
			InterruptedException {
		super.setup(context);
		try {
			final String ingestWithReducerStr = context.getConfiguration().get(
					AbstractMapReduceIngest.INGEST_PLUGIN_KEY);
			final byte[] ingestWithReducerBytes = ByteArrayUtils.byteArrayFromString(ingestWithReducerStr);
			ingestWithReducer = PersistenceUtils.fromBinary(
					ingestWithReducerBytes,
					IngestWithReducer.class);
			globalVisibility = context.getConfiguration().get(
					AbstractMapReduceIngest.GLOBAL_VISIBILITY_KEY);
			final String primaryIndexIdStr = context.getConfiguration().get(
					AbstractMapReduceIngest.PRIMARY_INDEX_ID_KEY);
			if (primaryIndexIdStr != null) {
				primaryIndexId = new ByteArrayId(
						primaryIndexIdStr);
			}
		}
		catch (final Exception e) {
			throw new IllegalArgumentException(
					e);
		}
	}
}