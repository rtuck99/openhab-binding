package com.qubular.vicare.internal;

import com.qubular.vicare.VicareService;
import com.qubular.vicare.internal.servlet.VicareServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.util.Hashtable;

@Component
public class VicareServiceImpl implements VicareService {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceImpl.class);

    @Activate
    public VicareServiceImpl(@Reference HttpService httpService) {
        logger.info("Activating ViCare Service");
        try {
            httpService.registerServlet(VicareServlet.CONTEXT_PATH, new VicareServlet(), new Hashtable<>(), httpService.createDefaultHttpContext());
        } catch (ServletException | NamespaceException e) {
            logger.error("Unable to register ViCare servlet", e);
        }
    }

    @Override
    public String helloWorld() {
        return "Hello World";
    }
}
