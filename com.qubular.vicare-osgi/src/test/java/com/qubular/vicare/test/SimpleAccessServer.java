package com.qubular.vicare.test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.BiConsumer;

public class SimpleAccessServer extends HttpServlet {
    private final BiConsumer<HttpServletRequest, HttpServletResponse> callback;

    public SimpleAccessServer(BiConsumer<HttpServletRequest, HttpServletResponse> callback) {
        this.callback = callback;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        callback.accept(req, resp);
    }
}
