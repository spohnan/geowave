package mil.nga.giat.geowave.vector;

import mil.nga.giat.geowave.index.ByteArrayId;

import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class FitToIndexDefaultFeatureCollection extends
		DefaultFeatureCollection
{
	private final ByteArrayId indexId;

	public FitToIndexDefaultFeatureCollection(
			final ByteArrayId indexId,
			final String id,
			final SimpleFeatureType memberType ) {
		super(
				id,
				memberType);
		this.indexId = indexId;
	}

	public FitToIndexDefaultFeatureCollection(
			final ByteArrayId indexId,
			final FeatureCollection<SimpleFeatureType, SimpleFeature> collection ) {
		super(
				collection);
		this.indexId = indexId;
	}

	public ByteArrayId getIndexId() {
		return indexId;
	}
}
