package org.basex.server.messages.auth;

import java.io.*;

public class LoginResultMessage implements Serializable {
  /** Success or failed. */
  private final boolean success;
  
  public LoginResultMessage(final boolean s) {
    success = s;
  }
  
  public boolean isSuccess() {
    return success;
  }
}
