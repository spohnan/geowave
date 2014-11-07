package mil.nga.giat.geowave.services.utils;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;

import mil.nga.giat.geowave.services.utils.data.AdapterInfo;
import mil.nga.giat.geowave.services.utils.data.IndexInfo;
import mil.nga.giat.geowave.services.utils.data.NamespaceInfo;

@Path("/info")
public interface InfoService
{

	// lists the namespaces in geowave
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces")
	public void getNamespaces(
			@Suspended final AsyncResponse asyncResponse );

	// lists some information about the given namespace
	// list of indices
	// -- list of adapters per index
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces/{namespace}")
	public void getNamespaceInfo(
			@Suspended final AsyncResponse asyncResponse,
			@PathParam("namespace") String namespace );

	// lists the indices associated with the given namespace
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces/{namespace}/indices")
	public void getIndices(
			@Suspended final AsyncResponse asyncResponse,
			@PathParam("namespace") String namespace );

	// lists some information about the given index
	// list of adapters
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces/{namespace}/indices/{indexid}")
	public void getIndexInfo(
			@Suspended final AsyncResponse asyncResponse,
			@PathParam("namespace") String namespace,
			@PathParam("indexid") String indexid );

	// lists the adapters associated with the given namespace
	// optionally specify an indexid
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces/{namespace}/adapters")
	public void getAdapters(
			@Suspended final AsyncResponse asyncResponse,
			@PathParam("namespace") String namespace,
			@DefaultValue("") @QueryParam("indexid") String indexid );

	// lists some information about the given adapter
	// optionally specify an indexid
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/namespaces/{namespace}/adapters/{adapterid}")
	public void getAdapterInfo(
			@Suspended final AsyncResponse asyncResponse,
			@PathParam("namespace") String namespace,
			@PathParam("adapterid") String adapterid,
			@DefaultValue("") @QueryParam("indexid") String indexid );
}
