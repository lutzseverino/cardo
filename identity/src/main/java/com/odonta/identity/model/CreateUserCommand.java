package com.odonta.identity.model;

public record CreateUserCommand(String email, String password, String name) {}
