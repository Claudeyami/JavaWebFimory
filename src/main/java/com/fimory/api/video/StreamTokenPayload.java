package com.fimory.api.video;

public record StreamTokenPayload(Long videoId,
                                 Long userId,
                                 long issuedAtEpochSec,
                                 long expiresAtEpochSec) {
}
