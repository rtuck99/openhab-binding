package com.qubular.openhab.binding.vicare.internal;

import org.junit.jupiter.api.Test;
import org.openhab.core.test.java.JavaOSGiTest;
import org.openhab.core.thing.ManagedThingProvider;
import org.openhab.core.thing.ThingProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class VicareBindingTest extends JavaOSGiTest {

    private ManagedThingProvider managedThingProvider;


    @Test
    public void testInjection() {
        System.out.println("Running a test");
        managedThingProvider = getService(ThingProvider.class, ManagedThingProvider.class);
        assertNotNull(managedThingProvider);
    }
}
