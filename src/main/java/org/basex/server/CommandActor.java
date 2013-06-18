package org.basex.server;

import static org.basex.core.Text.*;

import org.basex.core.*;
import org.basex.io.out.*;
import org.basex.server.messages.*;

import akka.actor.*;
import akka.event.*;
import akka.routing.*;

/**
 * Executes a given database command. This is a blocking operation as
 * the BaseX core is blocking at the moment. Therefore, this actor should only
 * be used in combination with a {@link Router} to avoid blocking the whole
 * application.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class CommandActor extends UntypedActor {
  /** Logging adapter. */
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  /** Database context. */
  private final Context dbContext;

  /**
   * Create Props for the command execution actor.
   * @param ctx database context
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps(final Context ctx) {
    return Props.create(CommandActor.class, ctx);
  }
  
  /**
   * Constructor.
   * @param ctx database context
   */
  public CommandActor(final Context ctx) {
    dbContext = ctx;
  }

  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof CommandMessage) {
      Command cmd = ((CommandMessage) msg).getCommand();
      log.info("Executed command: {}", cmd);
      
      if (((CommandMessage) msg).isSendResult()) {
        // execute command and send {RESULT}
        String info;
        Writer w = new Writer();
        try {
          // run command
          cmd.execute(dbContext, new EncodingOutput(w.getOutputStream()));
          info = cmd.info();

          w.writeTerminator().writeString(info).writeSuccess(true);
        } catch(final BaseXException ex) {
          info = ex.getMessage();
          if(info.startsWith(INTERRUPTED)) info = TIMEOUT_EXCEEDED;

          w.writeTerminator().writeString(info).writeSuccess(false);
        }
        w.send(getSender(), getSelf());
      } else {
        try {
          cmd.execute(dbContext);
          new Writer().writeString(cmd.info()).writeSuccess(true).send(getSender(), getSelf());
        } catch(final BaseXException ex) {
          new Writer().writeString(ex.getMessage()).writeSuccess(false).send(getSender(), getSelf());
        }
      }
    } else {
      unhandled(msg);
    }
  }
}
