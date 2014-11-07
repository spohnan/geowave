package mil.nga.giat.geowave.services.utils.data;

public class IndexInfo
{
	private String id;
	private AdapterInfo[] adapters;

	public IndexInfo(
			String id,
			AdapterInfo[] adapters ) {
		this.id = id;
		this.adapters = adapters;
	}

	public String getId() {
		return id;
	}

	public void setId(
			String id ) {
		this.id = id;
	}

	public AdapterInfo[] getAdapters() {
		return adapters;
	}

	public void setAdapters(
			AdapterInfo[] adapters ) {
		this.adapters = adapters;
	}
}
