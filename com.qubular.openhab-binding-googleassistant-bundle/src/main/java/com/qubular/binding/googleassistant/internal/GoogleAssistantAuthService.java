package com.qubular.binding.googleassistant.internal;

import com.qubular.binding.googleassistant.internal.servlet.OAuthRedirectServlet;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantBindingConfig;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;

@Component(service = GoogleAssistantAuthService.class, configurationPid = "binding.googleAssistant.authService")
@NonNullByDefault
public class GoogleAssistantAuthService {
    private HttpClientFactory httpClientFactory;
    private @NonNullByDefault({}) HttpService httpService;
    private OAuthService oauthService;
    private GoogleAssistantBindingConfig bindingConfig;
    private @NonNullByDefault({}) BundleContext bundleContext;

    private final Logger logger = LoggerFactory.getLogger(GoogleAssistantAuthService.class);
    private @Nullable OAuthRedirectServlet servlet;

    @Activate
    public GoogleAssistantAuthService(
            @Reference HttpClientFactory httpClientFactory,
            @Reference HttpService httpService,
          @Reference OAuthService oauthService,
          @Reference GoogleAssistantBindingConfig bindingConfig) {
        this.httpClientFactory = httpClientFactory;
        this.httpService = httpService;
        this.oauthService = oauthService;
        this.bindingConfig = bindingConfig;
        logger.debug("Constructed: GoogleAssistantAuthService httpService = {}", httpService);
    }

    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        try {
            bundleContext = componentContext.getBundleContext();
            servlet = createServlet();
            httpService.registerServlet(OAuthRedirectServlet.CONTEXT_PATH, servlet, new Hashtable<>(),
                    httpService.createDefaultHttpContext());
        } catch (NamespaceException | ServletException | IOException e) {
            logger.warn("Error during Google Assistant servlet startup", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        httpService.unregister(OAuthRedirectServlet.CONTEXT_PATH);
    }

    /**
     * Creates a new servlet
     *
     * @return the newly created servlet
     * @throws IOException thrown when an HTML template could not be read
     */
    private OAuthRedirectServlet createServlet() throws IOException {
        HttpClient httpClient = httpClientFactory.getCommonHttpClient();
        return new OAuthRedirectServlet(oauthService, bindingConfig, httpClient);
    }

    public void setCredentials(OAuthService.ClientCredentials credentials) {
        oauthService.setCredentials(credentials);
        servlet.setCredentials(credentials);
    }
}
