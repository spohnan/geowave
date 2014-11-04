package mil.nga.giat.geowave.webservices.rest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import mil.nga.giat.geowave.accumulo.metadata.AccumuloAdapterStore;
import mil.nga.giat.geowave.accumulo.AccumuloDataStore;
import mil.nga.giat.geowave.accumulo.metadata.AccumuloIndexStore;
import mil.nga.giat.geowave.vector.adapter.FeatureDataAdapter;
import mil.nga.giat.geowave.store.adapter.AdapterStore;
import mil.nga.giat.geowave.store.adapter.DataAdapter;
import mil.nga.giat.geowave.store.index.Index;
import mil.nga.giat.geowave.store.index.IndexStore;
import mil.nga.giat.geowave.utils.GeowaveUtils;
import mil.nga.giat.geowave.webservices.rest.data.DatastoreEncoder;
import mil.nga.giat.geowave.webservices.rest.data.FeatureTypeEncoder;
import mil.nga.giat.geowave.webservices.rest.data.GeoserverPublisher;
import mil.nga.giat.geowave.webservices.rest.data.GeoserverReader;
import mil.nga.giat.geowave.webservices.rest.data.HttpUtils;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.opengis.feature.simple.SimpleFeatureType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * Geoserver facades with default GeoWave configuration to
 * 		publish data stores
 * 		publish layers
 * 		get/set styles
 * 		list GeoWave data stores, with zookeepers, accumulo instance and namespace of each
 * 		list all GeoWave layers, and list layers by namespace
 *
 */

@Path("/services")
public class Services
{

	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/geowaveNamespaces")
	public static String getGeowaveNamespaces() {

		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			Document document = docBuilder.newDocument();
			Element rootElement = document.createElement("GeowaveNamespaces");
			document.appendChild(rootElement);
			
			Collection<String> namespaces = GeowaveUtils.getGeowaveNamespaces();

			for (String namespace : namespaces) {
				Element nsElement = document.createElement("namespace");
				nsElement.appendChild(document.createTextNode(namespace));
				rootElement.appendChild(nsElement);
			}
			StringWriter writer = new StringWriter();

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(writer);
			transformer.transform(source, result);
			return writer.toString();
		}
		catch (AccumuloException | AccumuloSecurityException | IOException | ParserConfigurationException | TransformerException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
	}

	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/geowaveLayers")
	public static String getGeowaveLayers() {
		try {
			return getGeowaveLayers(GeowaveUtils.getGeowaveNamespaces());
		}
		catch (AccumuloException | AccumuloSecurityException | IOException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
	}
	
	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/geowaveLayers/{namespace}")
	public static String getGeowaveLayers(@PathParam("namespace")String namespace) {
		Collection<String> namespaces = new ArrayList<String>();
		namespaces.add(namespace);
		return getGeowaveLayers(namespaces);
	}

	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/getGeowaveDatastores")
	public static String getGeowaveDatastores() {
		Collection<DatastoreEncoder> datastores = new ArrayList<DatastoreEncoder>();
		try {
			loadProperties();

			GeoserverReader reader = new GeoserverReader(geoserverUrl, geoserverUsername, geoserverPassword);
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(new ByteArrayInputStream((reader.getDatastores("geowave")).getBytes()));
			NodeList nodeList = document.getDocumentElement().getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE && "dataStore".equals(node.getNodeName())) {
					for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
						if (child.getNodeType() == Node.ELEMENT_NODE && "atom:link".equals(child.getNodeName())) {
							NamedNodeMap attributes = child.getAttributes();
				            for (int ii = 0; ii < attributes.getLength(); ii++) {
				                Node attribute = attributes.item(ii);
				                if ("href".equals(attribute.getNodeName())) {
				                	datastores.add(new DatastoreEncoder(HttpUtils.get(attribute.getNodeValue(), "",
		                					geoserverUsername, geoserverPassword)));
				                }
				            }
						}
					}				
				}
			}
			
			if (datastores.size() == 0)
				return "<datastores/>";
			else {
				String value = "<datastores>";
				for (DatastoreEncoder datastore : datastores) {
					value += datastore.xmlFormat(false);
				}
				value += "</datastores>";
				return value;
			}
		}
		catch(IOException | ParserConfigurationException | SAXException e) {}

		return null;
	}

	@POST
	@Path("/publishDataStore")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public static Response publishDataStore(MultivaluedMap<String, String> parameter) {
		String namespace = null;
		for (String key : parameter.keySet()) {
			if (key.equals("namespace"))
				namespace = parameter.getFirst(key);
		}
		return publishDataStore(namespace);
	}

	@POST
	@Path("/publishDataStore/{namespace}")
	@Consumes(MediaType.TEXT_PLAIN)
	public static Response publishDataStore(@PathParam("namespace")String namespace) {
		boolean flag = false;
		if (namespace != null) namespace.replaceAll("\\s+", "");

		if (namespace != null && namespace.length() > 0)
			flag = createDataStore(namespace);
		else
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("No value entered for Geowave Namespace.").build();

		if (flag)
			return Response.status(Status.CREATED).entity("Datastore published.").build();
		else
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error occurred.").build();
	}

	@POST
	@Path("/publishLayer")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public static Response publishLayer(MultivaluedMap<String, String> parameter) {
		String dataStore = null;
		String layer = null;
		for (String key : parameter.keySet()) {
			if (key.equals("dataStore"))
				dataStore = parameter.getFirst(key);
			else if (key.equals("layer"))
				layer = parameter.getFirst(key);
		}
		return publishLayer(dataStore, layer);
	}

	@POST
	@Path("/publishLayer/{dataStore}")
	@Consumes(MediaType.TEXT_PLAIN)
	public static Response publishLayer(@PathParam("dataStore")String dataStore, @QueryParam("name")String name) {
		boolean flag = false;
		if (dataStore != null) dataStore.replaceAll("\\s+", "");
		if (name != null) name.replaceAll("\\s+", "");

		if (dataStore != null && dataStore.length() > 0
				&& name != null && name.length() > 0)
			flag = createLayer(dataStore, name);

		if (flag)
			return Response.status(Status.CREATED).entity("Layer published.").build();
		else
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error occurred.").build();
	}

	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/getStyles")
	public static String getStyles() {
		try {
			loadProperties();

			GeoserverReader reader = new GeoserverReader(geoserverUrl, geoserverUsername, geoserverPassword);
			return reader.getStyles();
		}
		catch(IOException e) {}

		return null;
	}

	@GET
	@Produces({MediaType.APPLICATION_XML})
	@Path("/getStyles/{styleName}")
	public static String getStyle(@PathParam("styleName")String styleName) {
		try {
			loadProperties();
			GeoserverReader reader = new GeoserverReader(geoserverUrl, geoserverUsername, geoserverPassword);
			return reader.getStyles(styleName);
		}
		catch(IOException e) {}

		return null;
	}

	@POST
	@Path("/publishStyle")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public static boolean publishStyle(@Context HttpServletRequest request) {
		File sld = null;
		String styleName = null;
		try {
			//checks whether there is a file upload request or not
			if (ServletFileUpload.isMultipartContent(request)) {
				FileItemFactory factory = new DiskFileItemFactory();
				ServletFileUpload fileUpload = new ServletFileUpload(factory);
				
				for(Object obj : fileUpload.parseRequest(request)) {
					// check if it represents an uploaded file
					if (obj instanceof FileItem) {
						FileItem item = (FileItem)obj;
						if (item.isFormField()) {
							sld = new File(item.getName());
							item.write(sld);
						}
						else {
							if (item.getFieldName().equals("STYLE_NAME"))
								styleName = item.getString();
						}
					}
				}
			}
			if (sld != null && styleName != null) {
				return publishStyle(styleName, sld);
			}
		}
		catch (Exception e) {}
		return false;
	}
	
	public static boolean publishStyle(String styleName, File sld) {
		try {
			loadProperties();
			GeoserverPublisher publisher = new GeoserverPublisher(geoserverUrl, geoserverUsername, geoserverPassword);
			return publisher.publishStyle(styleName, sld);
		}
		catch (IOException e) {}
		return false;
	}
	
	@PUT
	@Path("/updateStyle")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public static boolean updateStyle(@Context HttpServletRequest request) {
		File sld = null;
		String styleName = null;
		try {
			//checks whether there is a file upload request or not
			if (ServletFileUpload.isMultipartContent(request)) {
				FileItemFactory factory = new DiskFileItemFactory();
				ServletFileUpload fileUpload = new ServletFileUpload(factory);
				
				for(Object obj : fileUpload.parseRequest(request)) {
					// check if it represents an uploaded file
					if (obj instanceof FileItem) {
						FileItem item = (FileItem)obj;
						if (item.isFormField()) {
							sld = new File(item.getName());
							item.write(sld);
						}
						else {
							if (item.getFieldName().equals("STYLE_NAME"))
								styleName = item.getString();
						}
					}
				}
			}
			if (sld != null && styleName != null) {
				return updateStyle(styleName, sld);
			}
		}
		catch (Exception e) {}
		return false;
	}

	public static boolean updateStyle(String styleName, File sld) {
		try {
			loadProperties();
			GeoserverPublisher publisher = new GeoserverPublisher(geoserverUrl, geoserverUsername, geoserverPassword);
			return publisher.updateStyle(styleName, sld);
		}
		catch (IOException e) {}
		return false;
	}
	
	private static String getGeowaveLayers(Collection<String> namespaces) {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			Document document = docBuilder.newDocument();
			Element rootElement = document.createElement("GeowaveLayers");
			document.appendChild(rootElement);

			for (String namespace : namespaces) {
				Element nsElement = document.createElement("namespace");
				rootElement.appendChild(nsElement);

				Element nameElement = document.createElement("name");
				nameElement.appendChild(document.createTextNode(namespace));
				nsElement.appendChild(nameElement);

				Element lsElement = document.createElement("layers");
				nsElement.appendChild(lsElement);

				AccumuloDataStore dataStore = GeowaveUtils.getDataStore(namespace);
				IndexStore indexStore = dataStore.getIndexStore();
				AdapterStore adapterStore = dataStore.getAdapterStore();
				if (indexStore instanceof AccumuloIndexStore) {
					Iterator<Index> indexIter = ((AccumuloIndexStore)indexStore).getIndices();
					while(indexIter.hasNext()) {
						indexIter.next();
						if (adapterStore instanceof AccumuloAdapterStore) {
							Iterator<DataAdapter<?>> iterator = ((AccumuloAdapterStore)adapterStore).getAdapters();
							while (iterator.hasNext()) {
								DataAdapter<?> dataAdapter = iterator.next();
								if (dataAdapter instanceof FeatureDataAdapter) {
									SimpleFeatureType simpleFeatureType = ((FeatureDataAdapter)dataAdapter).getType();

									Element lElement = document.createElement("layer");
									lElement.appendChild(document.createTextNode(simpleFeatureType.getTypeName()));
									lsElement.appendChild(lElement);
								}
							}
						}
					}
				}
			}

			StringWriter writer = new StringWriter();

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult result = new StreamResult(writer);

			transformer.transform(source, result);

			return writer.toString();
		}
		catch (AccumuloException | AccumuloSecurityException | IOException | ParserConfigurationException | TransformerException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
	}

	private static boolean createDataStore(String namespace) {
		boolean flag = false;
		try {
			DatastoreEncoder encoder = new DatastoreEncoder();
			encoder.setName(namespace);
			encoder.setType("GeoWave DataStore");
			encoder.setEnabled(true);

			loadProperties();
			
			Map<String, String> cp = new HashMap<String, String>();
			cp.put("ZookeeperServers", zookeeperUrl);
			cp.put("Password", geowavePassword);
			cp.put("Namespace", namespace);
			cp.put("UserName", geowaveUsername);
			cp.put("InstanceName", instanceName);

			encoder.setConnectionParameters(cp);

			GeoserverPublisher publisher = new GeoserverPublisher(geoserverUrl, geoserverUsername, geoserverPassword);

			if (publisher.datastoreExist(geoserverWorkspace, namespace)) {
				LOGGER.info("Datastore: " + namespace + " already exists.");
			}
			else {
				flag = publisher.createDatastore(geoserverWorkspace, encoder);
			}
		}
		catch (IOException e) {
			LOGGER.error("Exception: " + e.getClass().getName());
			LOGGER.error("Message: " + e.getMessage());
		}
		return flag;
	}

	private static boolean createLayer(String dataStore, String layerName) {
		boolean flag = false;
		try {
			FeatureTypeEncoder layer = new FeatureTypeEncoder();

			layer.setName(layerName);
			layer.setNativeName(layerName);
			layer.setTitle(layerName);

			Map<String, Collection<String>> keywords = new LinkedHashMap<String, Collection<String>>();
			Collection<String> temp = new ArrayList<String>();
			temp.add(layerName);
			keywords.put("string", temp);
			layer.setKeywords(keywords);

			LOGGER.info("GeoServer rest input: " + layer.toString());

			loadProperties();

			GeoserverPublisher publisher = new GeoserverPublisher(geoserverUrl, geoserverUsername, geoserverPassword);

			if (publisher.layerExist(layerName)) {
				LOGGER.info("Layer: " + layerName + " already exists.");
			}
			else {
				flag = publisher.publishLayer(geoserverWorkspace, dataStore, layer);
			}
		}
		catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
		return flag;
	}

	private static void loadProperties() throws IOException {
		if (!loaded) {
			// load geowave properties
			Properties prop = new Properties();
			String propFileName = "mil/nga/giat/geowave/utils/config.properties";
			InputStream inputStream = Services.class.getClassLoader().getResourceAsStream(propFileName);
			if (inputStream == null)
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			prop.load(inputStream);
			
			// load geoserver properties
			propFileName = "mil/nga/giat/geowave/webservices/rest/config.properties";
			inputStream = Services.class.getClassLoader().getResourceAsStream(propFileName);
			if (inputStream == null)
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			prop.load(inputStream);

			zookeeperUrl = prop.getProperty("zookeeperUrl");
			instanceName = prop.getProperty("instanceName");
			geowaveUsername = prop.getProperty("geowave_username");
			geowavePassword = prop.getProperty("geowave_password");

			geoserverUrl = prop.getProperty("geoserver_url");
			geoserverUsername = prop.getProperty("geoserver_username");
			geoserverPassword = prop.getProperty("geoserver_password");

			geoserverWorkspace = prop.getProperty("geoserver_workspace");

			loaded = true;
		}
	}
	
	private static boolean loaded = false;
	private static String zookeeperUrl;
	private static String instanceName;
	private static String geowaveUsername;
	private static String geowavePassword;

	private static String geoserverUrl;

	private static String geoserverUsername;
	private static String geoserverPassword;

	private static String geoserverWorkspace;

	private final static Logger LOGGER = Logger.getLogger(Services.class);
}
