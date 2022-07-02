package com.qubular.vicare.internal;

import com.qubular.vicare.ChallengeStore;
import com.qubular.vicare.HttpClientProvider;
import com.qubular.vicare.TokenStore;
import com.qubular.vicare.VicareService;
import com.qubular.vicare.internal.servlet.VicareServlet;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Hashtable;

@Component
public class VicareServiceImpl implements VicareService {
    private static final Logger logger = LoggerFactory.getLogger(VicareServiceImpl.class);

    @Activate
    public VicareServiceImpl(@Reference HttpService httpService,
                             @Reference ChallengeStore<?> challengeStore,
                             @Reference ConfigurationAdmin configurationAdmin,
                             @Reference HttpClientProvider httpClientProvider,
                             @Reference TokenStore tokenStore) {
        logger.info("Activating ViCare Service");
        try {
            Configuration configuration = configurationAdmin.getConfiguration(CONFIG_PID, CONFIG_ACCESS_SERVER_URI);
            VicareServiceConfiguration config = new VicareServiceConfiguration(configuration);
            VicareServlet servlet = new VicareServlet(challengeStore, tokenStore, config.getAccessServerURI(), httpClientProvider, config.getClientId());
            httpService.registerServlet(VicareServlet.CONTEXT_PATH, servlet, new Hashtable<>(), httpService.createDefaultHttpContext());
        } catch (ServletException | NamespaceException e) {
            logger.error("Unable to register ViCare servlet", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
