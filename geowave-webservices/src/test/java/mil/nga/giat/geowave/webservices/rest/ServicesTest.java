package mil.nga.giat.geowave.webservices.rest;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServicesTest
{
	private static Services services;
	
	@BeforeClass
	public static void startClient() {
		WebTarget t = ClientBuilder.newClient().target("localhost:9095/rest/services");
		services = WebResourceFactory.newResource(Services.class, t);
	}
	
	@Test
	public void test1() {
		
	}
}
