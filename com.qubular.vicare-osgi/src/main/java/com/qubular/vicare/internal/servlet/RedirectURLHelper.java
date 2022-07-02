package com.qubular.vicare.internal.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static java.util.Optional.ofNullable;

class RedirectURLHelper {
    private static final Logger logger = LoggerFactory.getLogger(RedirectURLHelper.class);

    static URL getNavigatedURL(HttpServletRequest req) {
        StringBuffer requestURL = req.getRequestURL();
        logger.debug("Generating redirect URI for {}", requestURL);

        String xForwardedHost = req.getHeader("X-Forwarded-Host");
        String host = xForwardedHost != null ? xForwardedHost : req.getServerName();
        String xForwardedProto = req.getHeader("X-Forwarded-Proto");
        String proto = xForwardedProto != null ? xForwardedProto : req.getScheme();
        String file = URI.create(req.getRequestURI()).getPath();
        Integer xForwardedPort = ofNullable(req.getHeader("X-Forwarded-Port")).map(Integer::valueOf).orElse(null);
        Integer port = xForwardedProto != null ?
                port(proto, xForwardedPort) :
                port(proto, port(proto, req.getServerPort()));
        logger.debug("Headers X-Forwarded-Host: {}, X-Forwarded-Proto: {}, X-Forwarded-Port: {}",
                xForwardedHost,
                xForwardedProto,
                xForwardedPort);
        try {
            return port != null ? new URL(proto, host, port, file) : new URL(proto, host, file);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unable to construct redirect URI", e);
        }
    }

    static URI getRedirectUri(HttpServletRequest req) {
        try {
            return getNavigatedURL(req).toURI().resolve("authCode");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to construct redirect URI", e);
        }
    }

    private static Integer port(String scheme, Integer port) {
        if (port == null) {
            return null;
        }
        return (("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443)) ? null : port;
    }
}
