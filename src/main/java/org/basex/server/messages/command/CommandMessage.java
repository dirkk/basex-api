package org.basex.server.messages.command;

import java.io.*;

public class CommandMessage implements Serializable {
  /** Command string. */
  private final String command;
  
  /**
   * A command message.
   * @param c command
   */
  public CommandMessage(final String c) {
    command = c;
  }

  /**
   * Get the command
   * @return command string
   */
  public String getCommand() {
    return command;
  }
}
