package org.basex.server.messages.command;

import java.io.*;

import org.jboss.netty.util.*;

import akka.util.*;

public class BindMessage implements Serializable {
  /** Name. */
  private final String name;
  /** Value. */
  private final String value;
  /** Type. */
  private final String type;
  
  /**
   * A command message representing the BIND command
   * @param n name
   * @param v value
   * @param t type
   */
  public BindMessage(final String n, final String v, final String t) {
    name = n;
    value = v;
    type = t;
  }

  /**
   * Get the path of the ADD command.
   * @return path
   */
  public String getName() {
    return name;
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
