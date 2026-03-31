package cn.aslight.workhub.domain.auth.model;

public record LocalUser(String userName, String displayName, String encodedPassword) {
}
