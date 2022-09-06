package com.qubular.binding.googleassistant.internal;

import com.qubular.binding.googleassistant.internal.config.GoogleAssistantBindingConfig;
import org.openhab.core.thing.ChannelUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

import static java.lang.Math.max;

@Component(service = ThrottlingChannelExecutor.class)
public class ThrottlingChannelExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ThrottlingChannelExecutor.class);
    private final ConcurrentMap<ChannelUID, PendingCommand> pendingCommandMap = new ConcurrentHashMap<>();
    private final GoogleAssistantBindingConfig config;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private class PendingCommand implements Runnable {
        Runnable nextCommand;

        private ScheduledFuture<?> scheduledFuture;

        private Instant commandTimestamp;
        /** Execute the first command immediately and record the timestamp. */
        PendingCommand() {
        }

        public synchronized void submitCommand(Runnable command, ScheduledExecutorService executorService) {
            Instant now = Instant.now();
            if (commandTimestamp != null && now.isBefore(commandTimestamp.plus(config.getApiChannelThrottleMs(), ChronoUnit.MILLIS))) {
                if (nextCommand != null) {
                    logger.debug("dropping throttled command {}", nextCommand);
                } else {
                    logger.debug("throttling command {}", command);
                }
                this.nextCommand = command;
                this.scheduledFuture = executorService.schedule(this, max(0, config.getApiChannelThrottleMs() - Duration.between(commandTimestamp, now).toMillis()), TimeUnit.MILLISECONDS);
            } else {
                // immediate execution
                commandTimestamp = now;
                command.run();
            }
        }

        private synchronized Runnable onStart() {
            Runnable command = nextCommand;
            nextCommand = null;
            commandTimestamp = Instant.now();
            scheduledFuture = null;
            return command;
        }

        @Override
        public void run() {
            Runnable command = onStart();
            command.run();
        }

        synchronized void cancel() {
            scheduledFuture.cancel(false);
            nextCommand = null;
        }
    }

    @Activate
    public ThrottlingChannelExecutor(@Reference GoogleAssistantBindingConfig config) {
        this.config = config;
    }

    public void dispose() {
        pendingCommandMap.forEach((uid, pendingCommand) -> pendingCommand.cancel());
        pendingCommandMap.clear();
        scheduledExecutorService.shutdown();
        try {
            scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void submitThrottledCommand(ChannelUID channelUID, Runnable command) {
        pendingCommandMap.computeIfAbsent(channelUID, c -> new PendingCommand())
                .submitCommand(command, scheduledExecutorService);
    }
}
