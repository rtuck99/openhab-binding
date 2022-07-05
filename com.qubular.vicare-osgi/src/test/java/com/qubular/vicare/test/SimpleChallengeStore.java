package com.qubular.vicare.test;

import com.qubular.vicare.ChallengeStore;
import org.osgi.service.component.annotations.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Optional.ofNullable;

/**
 * Stores the challenges but doesn't bother to expire or persist them
 */
@Component(service = ChallengeStore.class)
public class SimpleChallengeStore implements ChallengeStore<SimpleChallengeStore.SimpleChallenge> {
    private final Map<String, SimpleChallenge> challenges = new HashMap<>();

    public static class SimpleChallenge implements Challenge {
        private final String challengeCode;
        private final String key;

        public SimpleChallenge() {
            this.challengeCode = UUID.randomUUID() + "-" + UUID.randomUUID();
            this.key = UUID.randomUUID().toString();
        }

        @Override
        public String getChallengeCode() {
            return challengeCode;
        }

        @Override
        public String getKey() {
            return key;
        }
    }

    SimpleChallenge currentChallenge;

    @Override
    public synchronized SimpleChallenge createChallenge() {
        SimpleChallenge simpleChallenge = new SimpleChallenge();
        challenges.put(simpleChallenge.getKey(), simpleChallenge);
        currentChallenge = simpleChallenge;
        return simpleChallenge;
    }

    @Override
    public synchronized Optional<SimpleChallenge> getChallenge(String challengeKey) {
        return ofNullable(challenges.get(challengeKey));
    }
}
