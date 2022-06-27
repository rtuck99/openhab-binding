package com.qubular.vicare.test;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VicareServiceTest {
    @Test
    public void testHelloWorld() {
        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        assertNotNull(bundleContext);
        assertTrue(false, "Test failed");
    }
}
