package com.fimory.api.user;

public record UpdatePreferenceRequest(String language, String theme, Boolean autoPlay) {
}
