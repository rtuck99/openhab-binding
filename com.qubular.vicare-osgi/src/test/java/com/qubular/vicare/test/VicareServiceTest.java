package com.qubular.vicare.test;

import com.qubular.vicare.VicareService;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

public class VicareServiceTest {

    private BundleContext bundleContext;
    private HttpClient httpClient;
    private VicareService vicareService;

    private <T> T getService(Class<T> clazz) {
        return bundleContext.getService(bundleContext.getServiceReference(clazz));
    }

    @BeforeEach
    public void setUp() throws Exception {
        bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        httpClient = new HttpClient();
        httpClient.start();
        vicareService = getService(VicareService.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        httpClient.stop();
    }

    @Test
    public void testHelloWorld() {
        assertNotNull(bundleContext);
        assertNotNull(vicareService);
        assertEquals("Hello World", vicareService.helloWorld());
    }

    @Test
    public void setupPageRenders() throws Exception {
        String contentAsString = httpClient.GET("http://localhost:9000/vicare/setup")
                .getContentAsString();
        assertTrue(contentAsString.contains("<title>Viessmann API Binding Setup</title>"), contentAsString);
    }
}
