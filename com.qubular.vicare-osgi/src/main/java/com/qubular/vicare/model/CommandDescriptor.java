package com.qubular.vicare.model;

import java.net.URI;
import java.util.List;

public class CommandDescriptor {
    private String name;
    private final boolean executable;
    private final List<ParamDescriptor> params;
    private final URI uri;

    public CommandDescriptor(String name, boolean executable, List<ParamDescriptor> params, URI uri) {
        this.name = name;
        this.executable = executable;
        this.params = params;
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public URI getUri() {
        return uri;
    }

    public boolean isExecutable() {
        return executable;
    }

    public List<ParamDescriptor> getParams() {
        return params;
    }
}
