package mil.nga.giat.geowave.services.utils.data;

public class NamespaceInfo
{
	private String id;
	private IndexInfo[] indices;

	public NamespaceInfo(
			String id,
			IndexInfo[] indices ) {
		this.id = id;
		this.indices = indices;
	}

	public String getId() {
		return id;
	}

	public void setId(
			String id ) {
		this.id = id;
	}

	public IndexInfo[] getIndices() {
		return indices;
	}

	public void setIndices(
			IndexInfo[] indices ) {
		this.indices = indices;
	}
}
