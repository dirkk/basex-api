package org.basex.server.messages.auth;

import java.io.Serializable;

public class CredentialsMessage implements Serializable {
  /** User name. */
  private final String user;
  /** Hashed password. */
  private final String password;
  
  public CredentialsMessage(final String u, final String p) {
    user = u;
    password = p;
  }
  
  public String getUser() {
    return user;
  }
  
  public String getPassword() {
    return password;
  }
}
