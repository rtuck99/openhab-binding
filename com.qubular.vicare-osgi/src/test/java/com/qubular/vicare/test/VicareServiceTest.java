package com.qubular.vicare.test;

import com.qubular.vicare.VicareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VicareServiceTest {

    private BundleContext bundleContext;

    private <T> T getService(Class<T> clazz) {
        return bundleContext.getService(bundleContext.getServiceReference(clazz));
    }

    @BeforeEach
    public void setUp() {
        bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    }
    @Test
    public void testHelloWorld() {
        assertNotNull(bundleContext);
        VicareService vicareService = getService(VicareService.class);
        assertNotNull(vicareService);
        assertEquals("Hello World", vicareService.helloWorld());

    }
}
