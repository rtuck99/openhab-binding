package com.qubular.vicare.internal;

import com.qubular.vicare.VicareService;
import org.osgi.service.component.annotations.Component;

@Component
public class VicareServiceImpl implements VicareService {
    @Override
    public String helloWorld() {
        return "Hello World";
    }
}
