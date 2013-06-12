package org.basex.server.messages.command;

import java.io.Serializable;

public class ResultMessage implements Serializable {
  /** Success. */
  private final boolean success;
  /** Info message. */
  private final String info;
  /** Result. */
  private final String result;
  
  /**
   * Default constructor.
   * @param s success
   * @param i info message
   * @param r result
   */
  public ResultMessage(final boolean s, final String i, final String r) {
    success = s;
    info = i;
    result = r;
  }
  
  public boolean isSuccess() {
    return success;
  }
  
  public String getInfo() {
    return info;
  }
  
  public String getResult() {
    return result;
  }
}
