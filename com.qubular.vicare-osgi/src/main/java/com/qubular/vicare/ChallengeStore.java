package com.qubular.vicare;

import java.util.Optional;

/**
 * Stores the challenges used for OAuth PKCE authentication. Consumers of this bundle
 * should implement this as a service.
 * TParam is an implementation-specific type that defines toString to return the
 * String representation of the challenge.
 */
public interface ChallengeStore<TChallenge extends ChallengeStore.Challenge> {
    interface Challenge {
        String getChallengeCode();

        String getKey();
    }
    TChallenge createChallenge();

    Optional<TChallenge> getChallenge(String challengeKey);
}
