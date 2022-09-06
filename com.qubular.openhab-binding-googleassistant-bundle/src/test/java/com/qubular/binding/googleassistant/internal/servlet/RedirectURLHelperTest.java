package com.qubular.binding.googleassistant.internal.servlet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class RedirectURLHelperTest {
    @Mock
    private HttpServletRequest httpServletRequest;
    private AutoCloseable mockitoHandle;

    @BeforeEach
    public void setUp() {
        mockitoHandle = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockitoHandle.close();
    }

    @Test
    public void httpsBehindReverseProxyStandardPorts() {
        when(httpServletRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpServletRequest.getHeader("X-Forwarded-Host")).thenReturn("www.openhab.org");
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(80);

        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org/openhab", url.toString());
    }

    @Test
    public void httpsBehindReverseProxyStandardPortsWithExplicitPort() {
        when(httpServletRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpServletRequest.getHeader("X-Forwarded-Host")).thenReturn("www.openhab.org");
        when(httpServletRequest.getHeader("X-Forwarded-Port")).thenReturn("443");
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(80);

        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org/openhab", url.toString());
    }

    @Test
    public void httpsBehindReverseProxyCustomPort() {
        when(httpServletRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpServletRequest.getHeader("X-Forwarded-Host")).thenReturn("www.openhab.org");
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(8080);

        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org/openhab", url.toString());
    }

    @Test
    public void httpsCustomPortBehindReverseProxyCustomPort() {
        when(httpServletRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpServletRequest.getHeader("X-Forwarded-Host")).thenReturn("www.openhab.org");
        when(httpServletRequest.getHeader("X-Forwarded-Port")).thenReturn("8081");
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(8080);

        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org:8081/openhab", url.toString());
    }

    @Test
    public void bareHttps() {
        when(httpServletRequest.getScheme()).thenReturn("https");
        when(httpServletRequest.getServerName()).thenReturn("www.openhab.org");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(443);
        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org/openhab", url.toString());
    }

    @Test
    public void bareHttpsCustomPort() {
        when(httpServletRequest.getScheme()).thenReturn("https");
        when(httpServletRequest.getServerName()).thenReturn("www.openhab.org");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(8081);
        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("https://www.openhab.org:8081/openhab", url.toString());
    }

    @Test
    public void bareHttp() {
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("www.openhab.org");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(80);
        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("http://www.openhab.org/openhab", url.toString());
    }

    @Test
    public void bareHttpCustomPort() {
        when(httpServletRequest.getScheme()).thenReturn("http");
        when(httpServletRequest.getServerName()).thenReturn("www.openhab.org");
        when(httpServletRequest.getRequestURI()).thenReturn("/openhab");
        when(httpServletRequest.getServerPort()).thenReturn(8080);
        URL url = RedirectURLHelper.getNavigatedURL(httpServletRequest);
        assertEquals("http://www.openhab.org:8080/openhab", url.toString());
    }
}