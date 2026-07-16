package io.github.lutzseverino.cardo.identity;

public final class IdentityPermissions {

  public static final String CLIENT_ID = "identity";

  public static final String READ = "read";
  public static final String WRITE = "write";

  public static final String USER_RESOURCE = "identity:user";

  public static final String PROFILE_READ = "profile:read";
  public static final String PROFILE_WRITE = "profile:write";
  public static final String USER_PROVISION = "user:provision";

  public static final String PROFILE_READ_AUTHORITY = "identity:profile:read";
  public static final String PROFILE_WRITE_AUTHORITY = "identity:profile:write";
  public static final String USER_PROVISION_AUTHORITY = "identity:user:provision";

  private IdentityPermissions() {}
}
