package org.basex.server.messages.command;

import java.io.*;

import org.jboss.netty.util.*;

import akka.util.*;

public class ContextMessage implements Serializable {
  /** Value. */
  private final String value;
  /** Type. */
  private final String type;
  
  /**
   * A command message representing the CONTEXT command
   * @param v value
   * @param t type
   */
  public ContextMessage(final String v, final String t) {
    value = v;
    type = t;
  }
  
  /**
   * Get the value.
   * @return value
   */
  public String getValue() {
    return value;
  }
  
  /**
   * Get the type.
   * @return type
   */
  public String getType() {
    return type;
  }
}
