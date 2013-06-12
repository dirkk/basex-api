package org.basex.server.messages.command;

import java.io.*;

import org.jboss.netty.util.*;

import akka.util.*;

public class CreateMessage implements Serializable {
  /** Path. */
  private final String name;
  /** Input. */
  private final String input;
  
  /**
   * A command message representing the ADD command
   * @param n name
   * @param i input
   */
  public CreateMessage(final String n, final ByteString i) {
    name = n;
    input = i.decodeString(CharsetUtil.UTF_8.name());
  }

  /**
   * Get the path of the ADD command.
   * @return path
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the input for the ADD command as old-fashioned input stream.
   * @return input stream
   */
  public InputStream getInput() {
    return ByteString.fromString(input).iterator().asInputStream();
  }
}
