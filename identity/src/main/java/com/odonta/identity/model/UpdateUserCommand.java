package com.odonta.identity.model;

public record UpdateUserCommand(String name, String avatarUrl, UserStatus status) {}
