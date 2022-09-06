package com.qubular.binding.googleassistant.internal.grpc;

import com.google.assistant.embedded.v1alpha2.*;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.protobuf.ByteString;
import com.qubular.binding.googleassistant.internal.GoogleAssistantService;
import com.qubular.binding.googleassistant.internal.OAuthService;
import io.grpc.*;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;
import com.qubular.binding.googleassistant.internal.config.GoogleAssistantBindingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@SuppressWarnings("PackageAccessibility")
public class GrpcGoogleAssistantService implements GoogleAssistantService {
    private final ManagedChannel managedChannel;
    private final EmbeddedAssistantGrpc.EmbeddedAssistantStub grpc;
    private final GoogleAssistantBindingConfig bindingConfig;
    private final OAuthService oauthService;
    private ByteString conversationState;

    private static final Logger logger = LoggerFactory.getLogger(GrpcGoogleAssistantService.class);

    private OAuthService.OAuthSession oauthSession;

    public GrpcGoogleAssistantService(GoogleAssistantBindingConfig bindingConfig,
                                      OAuthService oauthService,
                                      OAuthService.OAuthSession oauthSession) {
        this.bindingConfig = bindingConfig;
        this.oauthService = oauthService;
        this.oauthSession = oauthSession;
        ChannelCredentials creds = CompositeChannelCredentials.create(
                TlsChannelCredentials.create(),
                MoreCallCredentials.from(
                        OAuth2CredentialsWithRefresh.newBuilder()
                                .setAccessToken(new AccessToken(oauthSession.getAccessToken(),
                                                Date.from(oauthSession.getBaseTime().plusSeconds(oauthSession.getExpiresIn()))))
                                .setRefreshHandler(() -> refreshToken())
                                .build()));
        managedChannel = Grpc.newChannelBuilderForAddress(bindingConfig.getApiHost(), bindingConfig.getApiPort(), creds)
                .build();
        grpc = EmbeddedAssistantGrpc.newStub(managedChannel);

    }

    private AccessToken refreshToken() throws IOException {
        try {
            logger.debug("checking token refresh");
            oauthSession = oauthService.maybeRefreshAccessToken(oauthSession).join();
            return new AccessToken(oauthSession.getAccessToken(),
                    Date.from(oauthSession.getBaseTime().plusSeconds(oauthSession.getExpiresIn())));
        } catch (CompletionException e) {
            logger.warn("Unexpected exception refreshing token.", e);
            throw new IOException(e);
        }
    }

    @Override
    public CompletableFuture<String> sendCommand(String command) {
        if (command == null) {
            return CompletableFuture.failedFuture(new NullPointerException("Command was null"));
        }
        logger.debug("Sending command {}", command);
        CompletableFuture<String> result = new CompletableFuture<>();
        StreamObserver<AssistRequest> requestObserver = grpc.assist(new StreamObserver<AssistResponse>() {
            StringBuilder sb = new StringBuilder();
            @Override
            public void onNext(AssistResponse value) {
                DialogStateOut dialogStateOut = value.getDialogStateOut();
                if (dialogStateOut != null) {
                    sb.append(dialogStateOut.getSupplementalDisplayText());
                    conversationState = dialogStateOut.getConversationState();
                }
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                result.complete(sb.toString());
            }
        });
        requestObserver.onNext(AssistRequest.newBuilder()
                .setConfig(AssistConfig.newBuilder()
                        .setAudioOutConfig(AudioOutConfig.newBuilder()
                                .setEncoding(AudioOutConfig.Encoding.LINEAR16)
                                .setSampleRateHertz(16000)
                                .setVolumePercentage(100)
                                .build())
                        .setDialogStateIn(dialogState())
                        .setDeviceConfig(DeviceConfig.newBuilder()
                                .setDeviceId(bindingConfig.getDeviceId())
                                .setDeviceModelId(bindingConfig.getDeviceModelId())
                                .build())
                        .setTextQuery(command)
                        .build())
                        .build());
        requestObserver.onCompleted();
        return result;
    }

    private DialogStateIn dialogState() {
        DialogStateIn.Builder builder = DialogStateIn.newBuilder();
        if (conversationState != null) {
            builder = builder
                    .setConversationState(conversationState);
        }
        return builder
                .setLanguageCode("en-GB")
                .setIsNewConversation(true)
                .build();
    }
}
