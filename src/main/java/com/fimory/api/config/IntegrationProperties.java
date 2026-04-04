package com.fimory.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration")
public class IntegrationProperties {

    private final Gemini gemini = new Gemini();
    private final Email email = new Email();

    public Gemini getGemini() {
        return gemini;
    }

    public Email getEmail() {
        return email;
    }

    public static class Gemini {
        private String chatApiKey = "";
        private String moderationApiKey = "";

        public String getChatApiKey() {
            return chatApiKey;
        }

        public void setChatApiKey(String chatApiKey) {
            this.chatApiKey = chatApiKey;
        }

        public String getModerationApiKey() {
            return moderationApiKey;
        }

        public void setModerationApiKey(String moderationApiKey) {
            this.moderationApiKey = moderationApiKey;
        }
    }

    public static class Email {
        private String user = "";
        private String password = "";

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
