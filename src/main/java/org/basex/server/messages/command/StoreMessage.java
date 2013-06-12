package org.basex.server.messages.command;

import java.io.*;

import org.jboss.netty.util.*;

import akka.util.*;

public class StoreMessage implements Serializable {
  /** Path. */
  private final String path;
  /** Input. */
  private final String input;
  
  /**
   * A command message representing the ADD command
   * @param p path
   * @param i input
   */
  public StoreMessage(final String p, final ByteString i) {
    path = p;
    input = i.decodeString(CharsetUtil.UTF_8.name());
  }

  /**
   * Get the path of the ADD command.
   * @return path
   */
  public String getPath() {
    return path;
  }

  /**
   * Returns the input for the ADD command as old-fashioned input stream.
   * @return input stream
   */
  public InputStream getInput() {
    return ByteString.fromString(input).iterator().asInputStream();
  }
}
