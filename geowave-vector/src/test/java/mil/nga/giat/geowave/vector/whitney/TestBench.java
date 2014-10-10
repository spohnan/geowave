package mil.nga.giat.geowave.vector.whitney;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import mil.nga.giat.geowave.accumulo.AccumuloDataStore;
import mil.nga.giat.geowave.accumulo.AccumuloIndexWriter;
import mil.nga.giat.geowave.accumulo.AccumuloRowId;
import mil.nga.giat.geowave.accumulo.BasicAccumuloOperations;
import mil.nga.giat.geowave.accumulo.query.AccumuloConstraintsQuery;
import mil.nga.giat.geowave.accumulo.query.ArrayToElementsIterator;
import mil.nga.giat.geowave.accumulo.query.ElementsToArrayIterator;
import mil.nga.giat.geowave.accumulo.util.AccumuloUtils;
import mil.nga.giat.geowave.index.ByteArrayId;
import mil.nga.giat.geowave.index.ByteArrayUtils;
import mil.nga.giat.geowave.index.HierarchicalNumericIndexStrategy.SubStrategy;
import mil.nga.giat.geowave.index.PersistenceUtils;
import mil.nga.giat.geowave.index.StringUtils;
import mil.nga.giat.geowave.index.sfc.data.MultiDimensionalNumericData;
import mil.nga.giat.geowave.index.sfc.tiered.TieredSFCIndexStrategy;
import mil.nga.giat.geowave.store.CloseableIterator;
import mil.nga.giat.geowave.store.adapter.DataAdapter;
import mil.nga.giat.geowave.store.adapter.MemoryAdapterStore;
import mil.nga.giat.geowave.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.store.index.CustomIdIndex;
import mil.nga.giat.geowave.store.index.Index;
import mil.nga.giat.geowave.store.index.IndexType;
import mil.nga.giat.geowave.store.query.SpatialQuery;
import mil.nga.giat.geowave.vector.adapter.FeatureCollectionDataAdapter;
import mil.nga.giat.geowave.vector.adapter.FeatureDataAdapter;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.iterators.IteratorUtil.IteratorScope;
import org.apache.accumulo.core.iterators.user.TransformingIterator;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.MiniAccumuloConfig;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.junit.Before;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.io.Files;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class TestBench
{
	private static enum AccumuloMode {
		MINI_ACCUMULO,
		DEPLOYED_ACCUMULO
	}

	private static enum IngestType {
		FEATURE_INGEST,
		COLLECTION_INGEST
	}

	private AccumuloMode mode = AccumuloMode.MINI_ACCUMULO;
	private static final String DEFAULT_MINI_ACCUMULO_PASSWORD = "Ge0wave";

	private String zookeeperUrl;
	private String instancename;
	private String username;
	private String password;

	private MiniAccumuloCluster miniAccumulo;
	private File tempDir;

	private SimpleFeatureType TYPE;

	// this is the original feature collection size before chipping
	final private int[] pointsPerColl = new int[] {
		100000,
		500000,
		1000000
	};

	final private int collsPerRegion = 15;
	final private int numRegions = 15;

	// this is the chunk size for the feature collection data adapter
	final private int[] pointsPerTile = new int[] {
		100,
		500,
		1000,
		5000,
		10000,
		50000
	};

	// min and max width/height of a region
	final double[] regMinMaxSize = new double[] {
		5.0,
		10.0
	};

	// min and max width/height of a collection
	final double[] collMinMaxSize = new double[] {
		0.5,
		1.0
	};

	final private String featureNamespace = "featureTest";
	final private String featureCollectionNamespace = "featureCollectionTest";

	private Geometry worldBBox;
	private final List<Geometry> smallBBoxes = new ArrayList<Geometry>();
	private final List<Geometry> medBBoxes = new ArrayList<Geometry>();
	private final List<Geometry> largeBBoxes = new ArrayList<Geometry>();

	private final int numSmallQueries = numRegions;
	private final int numMedQueries = numRegions;
	private final int numLargeQueries = numRegions;

	List<Long> ingestRuntimes = new ArrayList<Long>();
	List<Long> worldQueryRuntimes = new ArrayList<Long>();
	List<Long> smallQueryRuntimes = new ArrayList<Long>();
	List<Long> medQueryRuntimes = new ArrayList<Long>();
	List<Long> largeQueryRuntimes = new ArrayList<Long>();

	@Before
	public void accumuloInit()
			throws AccumuloException,
			AccumuloSecurityException,
			IOException,
			InterruptedException,
			SchemaException {

		final String miniEnabled = System.getProperty("miniAccumulo");

		if ((miniEnabled != null) && miniEnabled.equalsIgnoreCase("false")) {
			mode = AccumuloMode.DEPLOYED_ACCUMULO;
		}

		TYPE = DataUtilities.createType(
				"TestPoint",
				"location:Point:srid=4326,dim1:Double,dim2:Double,dim3:Double,startTime:Date,stopTime:Date,index:String");

		if (mode == AccumuloMode.MINI_ACCUMULO) {
			tempDir = Files.createTempDir();
			tempDir.deleteOnExit();

			System.out.println(tempDir.getAbsolutePath());

			final MiniAccumuloConfig config = new MiniAccumuloConfig(
					tempDir,
					password);
			config.setNumTservers(4);
			miniAccumulo = new MiniAccumuloCluster(
					config);
			miniAccumulo.start();
			zookeeperUrl = miniAccumulo.getZooKeepers();
			instancename = miniAccumulo.getInstanceName();
			username = "root";
			password = DEFAULT_MINI_ACCUMULO_PASSWORD;
		}
		else if (mode == AccumuloMode.DEPLOYED_ACCUMULO) {
			zookeeperUrl = System.getProperty("zookeeperUrl");
			instancename = System.getProperty("instance");
			username = System.getProperty("username");
			password = System.getProperty("password");
		}
		else {
			// intentionally left blank
		}
	}

	@Test
	public void runBenchmarks()
			throws AccumuloException,
			AccumuloSecurityException,
			SchemaException,
			IOException,
			InterruptedException,
			TableNotFoundException {

		ingestFeatureData();
		simpleFeatureTest();

		ingestCollectionData();
		redistributeData();
		featureCollectionTest();

		// write the results to a series of files
		saveRuntimes();
	}

	private void saveRuntimes()
			throws FileNotFoundException,
			UnsupportedEncodingException {

		// write ingest runtimes
		PrintWriter writer = new PrintWriter(
				"ingestRuntimes.txt",
				"UTF-8");

		for (final Long runtime : ingestRuntimes) {
			writer.println(Long.toString(runtime));
		}
		writer.close();

		// write the world query runtimes
		writer = new PrintWriter(
				"worldQueryRuntimes.txt",
				"UTF-8");

		for (final Long runtime : worldQueryRuntimes) {
			writer.println(Long.toString(runtime));
		}
		writer.close();

		// write the small query runtimes
		writer = new PrintWriter(
				"smallQueryRuntimes.txt",
				"UTF-8");

		for (final Long runtime : smallQueryRuntimes) {
			writer.println(Long.toString(runtime));
		}
		writer.close();

		// write the medium query runtimes
		writer = new PrintWriter(
				"medQueryRuntimes.txt",
				"UTF-8");

		for (final Long runtime : medQueryRuntimes) {
			writer.println(Long.toString(runtime));
		}
		writer.close();

		// write the large query runtimes
		writer = new PrintWriter(
				"largeQueryRuntimes.txt",
				"UTF-8");

		for (final Long runtime : largeQueryRuntimes) {
			writer.println(Long.toString(runtime));
		}
		writer.close();
	}

	private void ingestFeatureData()
			throws AccumuloException,
			AccumuloSecurityException,
			IOException,
			TableNotFoundException {
		System.out.println("****************************************************************************");
		System.out.println("                         Ingesting Feature Data                             ");
		System.out.println("****************************************************************************");

		// initialize the feature data adapter
		final BasicAccumuloOperations featureOperations = new BasicAccumuloOperations(
				zookeeperUrl,
				instancename,
				username,
				password,
				featureNamespace);
		final AccumuloDataStore featureDataStore = new AccumuloDataStore(
				featureOperations);
		final AccumuloIndexWriter featureWriter = new AccumuloIndexWriter(
				IndexType.SPATIAL_VECTOR.createDefaultIndex(),
				featureOperations,
				featureDataStore);
		final FeatureDataAdapter featureAdapter = new FeatureDataAdapter(
				TYPE);
		featureOperations.deleteAll();
		ingestData(
				IngestType.FEATURE_INGEST,
				featureWriter,
				featureAdapter);
		featureWriter.close();
		System.out.println();
	}

	private void ingestCollectionData()
			throws AccumuloException,
			AccumuloSecurityException,
			IOException,
			TableNotFoundException {
		// ingest feature collection data multiple times with different settings
		// for batchSize
		System.out.println("****************************************************************************");
		System.out.println("                    Ingesting Feature Collection Data                       ");
		System.out.println("****************************************************************************");

		System.out.print("*** Original Collection Sizes: [ ");
		for (final int numPts : pointsPerColl) {
			System.out.print(numPts + " ");
		}
		System.out.println("]\n");

		for (final int batchSize : pointsPerTile) {
			System.out.println("*** Features per tilespace: " + batchSize);
			final BasicAccumuloOperations featureCollectionOperations = new BasicAccumuloOperations(
					zookeeperUrl,
					instancename,
					username,
					password,
					featureCollectionNamespace + batchSize);
			final AccumuloDataStore featureCollectionDataStore = new AccumuloDataStore(
					featureCollectionOperations);
			final AccumuloIndexWriter featureCollectionWriter = new AccumuloIndexWriter(
					IndexType.SPATIAL_VECTOR.createDefaultIndex(),
					featureCollectionOperations,
					featureCollectionDataStore);
			final FeatureCollectionDataAdapter featureCollectionAdapter = new FeatureCollectionDataAdapter(
					TYPE,
					batchSize);
			featureCollectionOperations.deleteAll();
			ingestData(
					IngestType.COLLECTION_INGEST,
					featureCollectionWriter,
					featureCollectionAdapter);
			featureCollectionWriter.close();
			System.out.println();
		}
	}

	private void ingestData(
			final IngestType ingestType,
			final AccumuloIndexWriter writer,
			final WritableDataAdapter adapter )
			throws AccumuloException,
			AccumuloSecurityException,
			IOException,
			TableNotFoundException {
		final Random rand = new Random(
				0);

		smallBBoxes.clear();
		medBBoxes.clear();
		largeBBoxes.clear();

		int numPoints = 0;
		int numColls = 0;
		long runtime = 0;

		final GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		final SimpleFeatureBuilder builder = new SimpleFeatureBuilder(
				TYPE);
		final NumberFormat format = NumberFormat.getIntegerInstance();
		format.setMaximumIntegerDigits(5);
		format.setMinimumIntegerDigits(5);
		int id = 1;
		for (int i = 0; i < numRegions; i++) {

			// width/height in degrees [range from 5 -10 degrees]
			final double[] regDims = new double[2];
			regDims[0] = (rand.nextDouble() * (regMinMaxSize[1] - regMinMaxSize[0])) + regMinMaxSize[0]; // width
			regDims[1] = (rand.nextDouble() * (regMinMaxSize[1] - regMinMaxSize[0])) + regMinMaxSize[0]; // height

			// pick the region center (lon/lat)
			final double[] regCenter = new double[2];
			regCenter[0] = ((rand.nextDouble() * 2.0) - 1.0) * (180.0 - regMinMaxSize[1]); // lon
			regCenter[1] = ((rand.nextDouble() * 2.0) - 1.0) * (90.0 - regMinMaxSize[1]); // lat

			// generate collections within the region
			for (int j = 0; j < collsPerRegion; j++) {

				// create a new collection
				final DefaultFeatureCollection coll = new DefaultFeatureCollection(
						null,
						TYPE);

				// width/height in degrees [range from 0.5 - 1.0 degrees]
				final double[] collDims = new double[2];
				collDims[0] = (rand.nextDouble() * (collMinMaxSize[1] - collMinMaxSize[0])) + collMinMaxSize[0]; // width
				collDims[1] = (rand.nextDouble() * (collMinMaxSize[1] - collMinMaxSize[0])) + collMinMaxSize[0]; // height

				// pick the collection center (lon/lat)
				final double[] collCenter = new double[2];
				collCenter[0] = ((rand.nextGaussian() * regDims[0]) / 2.0) + regCenter[0]; // lon
				collCenter[1] = ((rand.nextGaussian() * regDims[1]) / 2.0) + regCenter[1]; // lat

				// generate each of the features within the collection
				for (int k = 0; k < pointsPerColl[j % pointsPerColl.length]; k++) {

					// generate a lon/lat coordinate within the bounds of the
					// current collection
					final double[] coord = new double[2];
					coord[0] = (rand.nextDouble() * collDims[0]) + (collCenter[0] - (collDims[0] / 2.0)); // lon
					coord[1] = (rand.nextDouble() * collDims[1]) + (collCenter[1] - (collDims[1] / 2.0)); // lat

					coord[0] = Math.min(
							Math.max(
									-180.0,
									coord[0]),
							180.0);
					coord[1] = Math.min(
							Math.max(
									-90.0,
									coord[1]),
							90.0);

					final Point point = geometryFactory.createPoint(new Coordinate(
							coord[0],
							coord[1]));

					builder.set(
							"location",
							point);
					builder.set(
							"index",
							"[" + i + "," + j + "," + k + "]");
					builder.set(
							"dim1",
							rand.nextDouble());
					builder.set(
							"dim2",
							rand.nextDouble());
					builder.set(
							"dim3",
							rand.nextDouble());

					// generate the feature
					final SimpleFeature feature = builder.buildFeature(format.format(id++));
					if (ingestType == IngestType.FEATURE_INGEST) {
						final long start = new Date().getTime();
						writer.write(
								adapter,
								feature);
						final long finish = new Date().getTime();
						runtime += (finish - start);
						numPoints++;
					}
					else {
						coll.add(feature);
						numPoints++;
					}
				}

				// write this feature collection to accumulo
				if (ingestType == IngestType.COLLECTION_INGEST) {
					final long start = new Date().getTime();
					writer.write(
							adapter,
							coll);
					final long finish = new Date().getTime();
					runtime += (finish - start);
					numColls++;
				}
			}

			// Generate a few queries for this region
			// town sized bounding box
			smallBBoxes.add(createBoundingBox(
					regCenter,
					0.05,
					0.05));

			// city sized bounding box
			medBBoxes.add(createBoundingBox(
					regCenter,
					1.0,
					1.0));

			// region sized bounding box
			largeBBoxes.add(createBoundingBox(
					regCenter,
					regDims[0],
					regDims[1]));
		}

		// world sized bounding box
		worldBBox = createBoundingBox(
				new double[] {
					0.0,
					0.0
				},
				360.0,
				180.0);

		// save the ingest runtime to the output list
		ingestRuntimes.add(runtime);

		System.out.println("*** Ingest runtime: " + runtime + " ms");
		System.out.println("*** Features Ingested: " + numPoints);
		if (ingestType == IngestType.COLLECTION_INGEST) {
			System.out.println("*** Collections Ingested: " + numColls);
		}
	}

	private Geometry createBoundingBox(
			final double[] centroid,
			final double width,
			final double height ) {

		final double north = centroid[1] + (height / 2.0);
		final double south = centroid[1] - (height / 2.0);
		final double east = centroid[0] + (width / 2.0);
		final double west = centroid[0] - (width / 2.0);

		final Coordinate[] coordArray = new Coordinate[5];
		coordArray[0] = new Coordinate(
				west,
				south);
		coordArray[1] = new Coordinate(
				east,
				south);
		coordArray[2] = new Coordinate(
				east,
				north);
		coordArray[3] = new Coordinate(
				west,
				north);
		coordArray[4] = new Coordinate(
				west,
				south);

		return new GeometryFactory().createPolygon(coordArray);
	}

	private void redistributeData()
			throws AccumuloException,
			AccumuloSecurityException,
			IOException,
			TableNotFoundException {
		// ingest feature collection data multiple times with different settings
		// for batchSize
		System.out.println("****************************************************************************");
		System.out.println("                  Redistributing Feature Collection Data                    ");
		System.out.println("****************************************************************************");

		for (final int tileSize : pointsPerTile) {
			System.out.println("*** Features per tilespace: " + tileSize);
			final BasicAccumuloOperations featureCollectionOperations = new BasicAccumuloOperations(
					zookeeperUrl,
					instancename,
					username,
					password,
					featureCollectionNamespace + tileSize);
			final AccumuloDataStore featureCollectionDataStore = new AccumuloDataStore(
					featureCollectionOperations);
			final AccumuloIndexWriter featureCollectionWriter = new AccumuloIndexWriter(
					IndexType.SPATIAL_VECTOR.createDefaultIndex(),
					featureCollectionOperations,
					featureCollectionDataStore);
			final FeatureCollectionDataAdapter featureCollectionAdapter = new FeatureCollectionDataAdapter(
					TYPE,
					tileSize);
			redistribute(
					tileSize,
					featureCollectionOperations,
					featureCollectionAdapter,
					featureCollectionWriter,
					featureCollectionDataStore);
			featureCollectionWriter.close();
			System.out.println();
		}
	}

	private void redistribute(
			final int tileSize,
			final BasicAccumuloOperations operations,
			final FeatureCollectionDataAdapter adapter,
			final AccumuloIndexWriter writer,
			final AccumuloDataStore dataStore )
			throws IOException,
			AccumuloSecurityException,
			AccumuloException,
			TableNotFoundException {

		final Index index = IndexType.SPATIAL_VECTOR.createDefaultIndex();
		final TieredSFCIndexStrategy tieredStrat = (TieredSFCIndexStrategy) index.getIndexStrategy();

		final String tablename = AccumuloUtils.getQualifiedTableName(
				featureCollectionNamespace + tileSize,
				StringUtils.stringFromBinary(index.getId().getBytes()));

		// first, detach the transforming iterators
		operations.getConnector().tableOperations().removeIterator(
				tablename,
				new IteratorSetting(
						FeatureCollectionDataAdapter.ARRAY_TO_ELEMENTS_PRIORITY,
						ArrayToElementsIterator.class).getName(),
				EnumSet.of(IteratorScope.scan));

		operations.getConnector().tableOperations().removeIterator(
				tablename,
				new IteratorSetting(
						FeatureCollectionDataAdapter.ELEMENTS_TO_ARRAY_PRIORITY,
						ElementsToArrayIterator.class).getName(),
				EnumSet.of(IteratorScope.scan));

		final long startTime = new Date().getTime();

		int numRedist = 0;
		int maxSize = Integer.MIN_VALUE;
		int minSize = Integer.MAX_VALUE;
		int meanSize = 0;
		int numColls = 0;

		final FeatureDataAdapter featAdapter = new FeatureDataAdapter(
				TYPE);

		// iterate over each tier
		for (final SubStrategy subStrat : tieredStrat.getSubStrategies()) {

			// create an index for this substrategy
			final CustomIdIndex customIndex = new CustomIdIndex(
					subStrat.getIndexStrategy(),
					index.getIndexModel(),
					index.getDimensionalityType(),
					index.getDataType(),
					index.getId());

			final AccumuloConstraintsQuery q = new AccumuloConstraintsQuery(
					Arrays.asList(new ByteArrayId[] {
						adapter.getAdapterId()
					}),
					customIndex,
					new SpatialQuery(
							worldBBox).getIndexConstraints(customIndex.getIndexStrategy()),
					null);

			// query at the specified index
			final CloseableIterator<DefaultFeatureCollection> itr = (CloseableIterator<DefaultFeatureCollection>) q.query(
					operations,
					new MemoryAdapterStore(
							new DataAdapter[] {
								adapter
							}),
					null);

			// iterate over each collection
			while (itr.hasNext()) {
				final SimpleFeatureCollection featColl = itr.next();

				maxSize = (featColl.size() > maxSize) ? featColl.size() : maxSize;
				minSize = (featColl.size() < minSize) ? featColl.size() : minSize;
				meanSize += featColl.size();
				numColls++;

				// if the collection size is greater than tilesize
				if (featColl.size() > tileSize) {

					numRedist++;

					// use the first feature to determine the index insertion id
					final MultiDimensionalNumericData bounds = featAdapter.encode(
							featColl.features().next(),
							index.getIndexModel()).getNumericData(
							index.getIndexModel().getDimensions());

					final List<ByteArrayId> ids = subStrat.getIndexStrategy().getInsertionIds(
							bounds);

					if (ids.size() > 1) {
						System.out.println("Multiple row ids returned for this entry?!");
					}

					// build a row id for deletion
					final AccumuloRowId rowId = new AccumuloRowId(
							ids.get(
									0).getBytes(),
							new byte[] {},
							adapter.getAdapterId().getBytes(),
							0);

					// delete this tile
					final boolean result = operations.delete(
							StringUtils.stringFromBinary(index.getId().getBytes()),
							new ByteArrayId(
									rowId.getRowId()),
							adapter.getAdapterId().getString(),
							null);

					if (result != true) {
						System.out.println("Row wasn't deleted successfully!");
					}

					// re-ingest the data
					writer.write(
							adapter,
							(DefaultFeatureCollection) featColl);
				}
			}
			itr.close();
		}

		final long stopTime = new Date().getTime();

		// finally, attach the transforming iterators
		final String modelString = ByteArrayUtils.byteArrayToString(PersistenceUtils.toBinary(index.getIndexModel()));
		final IteratorSetting decompSetting = new IteratorSetting(
				FeatureCollectionDataAdapter.ARRAY_TO_ELEMENTS_PRIORITY,
				ArrayToElementsIterator.class);
		decompSetting.addOption(
				ArrayToElementsIterator.MODEL,
				modelString);
		decompSetting.addOption(
				TransformingIterator.MAX_BUFFER_SIZE_OPT,
				Integer.toString(512000000));
		operations.getConnector().tableOperations().attachIterator(
				tablename,
				decompSetting,
				EnumSet.of(IteratorScope.scan));

		final IteratorSetting builderSetting = new IteratorSetting(
				FeatureCollectionDataAdapter.ELEMENTS_TO_ARRAY_PRIORITY,
				ElementsToArrayIterator.class);
		builderSetting.addOption(
				ElementsToArrayIterator.MODEL,
				modelString);
		builderSetting.addOption(
				TransformingIterator.MAX_BUFFER_SIZE_OPT,
				Integer.toString(512000000));
		operations.getConnector().tableOperations().attachIterator(
				tablename,
				builderSetting,
				EnumSet.of(IteratorScope.scan));

		System.out.println("*** Collections Redistributed: " + numRedist);
		System.out.println("*** Total Number of Collections: " + numColls);
		System.out.println("*** Runtime: " + (stopTime - startTime) + " ms");
		System.out.println("*** Stats Before Redistribution");
		System.out.println("***   Min Collection Size: " + minSize);
		System.out.println("***   Max Collection Size: " + maxSize);
		System.out.println("***   Mean Collection Size: " + ((double) meanSize / (double) numColls));
	}

	private void simpleFeatureTest()
			throws AccumuloException,
			AccumuloSecurityException,
			SchemaException,
			IOException,
			InterruptedException {

		System.out.println("****************************************************************************");
		System.out.println("                       Testing FeatureDataAdapter                           ");
		System.out.println("****************************************************************************");

		final BasicAccumuloOperations featureOperations = new BasicAccumuloOperations(
				zookeeperUrl,
				instancename,
				username,
				password,
				featureNamespace);
		final AccumuloDataStore featureDataStore = new AccumuloDataStore(
				featureOperations);
		final FeatureDataAdapter featureAdapter = new FeatureDataAdapter(
				TYPE);

		System.out.println("*** World Query");
		long runtime = simpleFeatureQuery(
				featureDataStore,
				featureAdapter,
				worldBBox);

		worldQueryRuntimes.add(runtime);

		System.out.println("*** Small Queries");
		for (int i = 0; (i < numSmallQueries) && (i < smallBBoxes.size()); i++) {
			System.out.println("***   Query " + (i + 1));
			runtime = simpleFeatureQuery(
					featureDataStore,
					featureAdapter,
					smallBBoxes.get(i));

			smallQueryRuntimes.add(runtime);
		}

		System.out.println("*** Medium Queries");
		for (int i = 0; (i < numMedQueries) && (i < medBBoxes.size()); i++) {
			System.out.println("***   Query " + (i + 1));
			runtime = simpleFeatureQuery(
					featureDataStore,
					featureAdapter,
					medBBoxes.get(i));

			medQueryRuntimes.add(runtime);
		}

		System.out.println("*** Large Queries");
		for (int i = 0; (i < numLargeQueries) && (i < largeBBoxes.size()); i++) {
			System.out.println("***   Query " + (i + 1));
			runtime = simpleFeatureQuery(
					featureDataStore,
					featureAdapter,
					largeBBoxes.get(i));

			largeQueryRuntimes.add(runtime);
		}
	}

	private long simpleFeatureQuery(
			final AccumuloDataStore featureDataStore,
			final FeatureDataAdapter featureAdapter,
			final Geometry geom )
			throws IOException {
		final long queryStart = new Date().getTime();
		final CloseableIterator<SimpleFeature> itr = featureDataStore.query(
				featureAdapter,
				IndexType.SPATIAL_VECTOR.createDefaultIndex(),
				new SpatialQuery(
						geom));

		int i = 0;
		while (itr.hasNext()) {
			itr.next();
			i++;
		}
		itr.close();
		final long queryStop = new Date().getTime();

		System.out.println("***     Query Runtime: " + (queryStop - queryStart) + " ms");
		System.out.println("***     Features: " + i);

		return (queryStop - queryStart);
	}

	private void featureCollectionTest()
			throws AccumuloException,
			AccumuloSecurityException,
			SchemaException,
			IOException,
			InterruptedException {

		System.out.println("****************************************************************************");
		System.out.println("                   Testing FeatureCollectionDataAdapter                     ");
		System.out.println("****************************************************************************");

		for (final int batchSize : pointsPerTile) {
			final BasicAccumuloOperations featureCollectionOperations = new BasicAccumuloOperations(
					zookeeperUrl,
					instancename,
					username,
					password,
					featureCollectionNamespace + batchSize);
			final AccumuloDataStore featureCollectionDataStore = new AccumuloDataStore(
					featureCollectionOperations);
			final FeatureCollectionDataAdapter featureCollectionAdapter = new FeatureCollectionDataAdapter(
					TYPE,
					batchSize);

			System.out.println("*** Features per tilespace: " + batchSize);

			System.out.println("*** World Query");
			long runtime = featureCollectionQuery(
					featureCollectionDataStore,
					featureCollectionAdapter,
					worldBBox);

			worldQueryRuntimes.add(runtime);

			System.out.println("*** Small Queries");
			for (int i = 0; (i < numSmallQueries) && (i < smallBBoxes.size()); i++) {
				System.out.println("***   Query " + (i + 1));
				runtime = featureCollectionQuery(
						featureCollectionDataStore,
						featureCollectionAdapter,
						smallBBoxes.get(i));

				smallQueryRuntimes.add(runtime);
			}

			System.out.println("*** Medium Queries");
			for (int i = 0; (i < numMedQueries) && (i < medBBoxes.size()); i++) {
				System.out.println("***   Query " + (i + 1));
				runtime = featureCollectionQuery(
						featureCollectionDataStore,
						featureCollectionAdapter,
						medBBoxes.get(i));

				medQueryRuntimes.add(runtime);
			}

			System.out.println("*** Large Queries");
			for (int i = 0; (i < numLargeQueries) && (i < largeBBoxes.size()); i++) {
				System.out.println("***   Query " + (i + 1));
				runtime = featureCollectionQuery(
						featureCollectionDataStore,
						featureCollectionAdapter,
						largeBBoxes.get(i));

				largeQueryRuntimes.add(runtime);
			}
		}
	}

	private long featureCollectionQuery(
			final AccumuloDataStore featureCollectionDataStore,
			final FeatureCollectionDataAdapter featureCollectionAdapter,
			final Geometry geom )
			throws IOException {
		final long queryStart = new Date().getTime();
		final CloseableIterator<DefaultFeatureCollection> itr = featureCollectionDataStore.query(
				featureCollectionAdapter,
				IndexType.SPATIAL_VECTOR.createDefaultIndex(),
				new SpatialQuery(
						geom));

		int i = 0;
		int j = 0;
		while (itr.hasNext()) {
			final SimpleFeatureCollection featColl = itr.next();
			j++;
			i += featColl.size();
		}
		itr.close();

		final long queryStop = new Date().getTime();
		System.out.println("***     Query Runtime: " + (queryStop - queryStart) + " ms");
		System.out.println("***     Features: " + i);
		System.out.println("***     Collections: " + j);

		return (queryStop - queryStart);
	}
}
