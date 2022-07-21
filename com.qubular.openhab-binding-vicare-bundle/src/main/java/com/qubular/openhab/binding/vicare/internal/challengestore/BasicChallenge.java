package com.qubular.openhab.binding.vicare.internal.challengestore;

import com.qubular.vicare.ChallengeStore;

import java.util.UUID;

public class BasicChallenge implements ChallengeStore.Challenge {
    private final String challengeCode;
    private final String key;

    public BasicChallenge() {
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
