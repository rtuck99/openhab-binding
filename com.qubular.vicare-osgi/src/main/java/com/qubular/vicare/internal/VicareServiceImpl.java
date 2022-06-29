package com.qubular.vicare.internal;

import com.qubular.vicare.VicareService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class VicareServiceImpl implements VicareService {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceImpl.class);
    @Activate
    public VicareServiceImpl(@Reference HttpService httpService) {
        logger.info("Activating ViCare Service");
    }

    @Override
    public String helloWorld() {
        return "Hello World";
    }
}
