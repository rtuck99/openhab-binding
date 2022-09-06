package com.qubular.binding.googleassistant.internal;

import java.util.concurrent.CompletableFuture;

public interface GoogleAssistantService extends EmbeddedAssistantService {

    CompletableFuture<String> sendCommand(String command);

}
