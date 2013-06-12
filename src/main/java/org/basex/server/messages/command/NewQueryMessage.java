package org.basex.server.messages.command;

import java.io.*;

import org.jboss.netty.util.*;

import akka.util.*;

public class NewQueryMessage implements Serializable {
  /** Query. */
  private final String query;
  
  /**
   * A command message representing the QUERY command
   * @param q query
   */
  public NewQueryMessage(final String q) {
    query = q;
  }

  /**
   * Get the query.
   * @return query
   */
  public String getQuery() {
    return query;
  }
}
