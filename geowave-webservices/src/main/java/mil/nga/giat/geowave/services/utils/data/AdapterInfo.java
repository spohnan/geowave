package mil.nga.giat.geowave.services.utils.data;

public class AdapterInfo
{
	private String id;
	private String type;

	public AdapterInfo(
			String id,
			String type ) {
		this.id = id;
		this.type = type;
	}

	public String getId() {
		return id;
	}

	public void setId(
			String id ) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(
			String type ) {
		this.type = type;
	}
}
