package org.basex.server.messages;

import org.basex.core.*;
import org.basex.server.*;

/**
 * A command message, including a database command to be executed by a
 * {@link CommandActor}.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class CommandMessage {
  /** Command to execute. */
  private final Command cmd;
  /** Send the result? */
  private final boolean sendResult;
  
  /**
   * Constructor. The result will not be returned to the sender.
   * @param c command to execute
   */
  public CommandMessage(final Command c) {
    this(c, false);
  }
  
  /**
   * Constructor.
   * @param c command to execute
   * @param s if true, the result should be returned as well.
   */
  public CommandMessage(final Command c, final boolean s) {
    cmd = c;
    sendResult = s;
  }
  
  /**
   * Get the command.
   * @return command
   */
  public Command getCommand() {
    return cmd;
  }
  
  /**
   * If true, the result of the command execution should also be 
   * returned to the sender.
   * @return return result
   */
  public boolean isSendResult() {
    return sendResult;
  }
}
