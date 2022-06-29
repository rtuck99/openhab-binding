package com.qubular.vicare.internal.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class VicareServlet extends HttpServlet {
    public static final String CONTEXT_PATH = "/vicare";
    private static final Logger logger = LoggerFactory.getLogger(VicareServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Fetching {}", req.getRequestURL());

        URI uri = URI.create(req.getRequestURI());
        String relPath = uri.getPath().replaceFirst(CONTEXT_PATH, "");

        switch (relPath) {
            case "/setup":
                renderSetupPage(req, resp);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void renderSetupPage(HttpServletRequest req, HttpServletResponse resp) {
        try (InputStream is = getClass().getResourceAsStream("setup.html")) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // TODO substitute variables

            resp.setContentType("text/html");
            try (ServletOutputStream os = resp.getOutputStream()) {
                os.print(html);
            }
        } catch (IOException e) {
            logger.warn("Unable to render setup page", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
