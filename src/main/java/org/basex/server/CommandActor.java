package org.basex.server;

import java.io.*;

import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.core.parse.*;
import org.basex.server.messages.command.*;
import org.jboss.netty.util.*;

import scala.concurrent.duration.*;

import akka.actor.*;
import akka.actor.SupervisorStrategy.Directive;
import akka.event.*;
import akka.japi.*;
import akka.remote.*;
import akka.util.*;

public class CommandActor extends UntypedActor {
  /** Database context. */
  protected final Context dbContext;
  /** Logging instance. */
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  /**
   * Create a new {@link Props} object, a configuration class, to create
   * a new actor.
   * 
   * @return props
   */
  public static Props mkProps(final Context c) {
    return Props.create(CommandActor.class, c);
  }

  /**
   * Default constructor.
   */
  public CommandActor(final Context c) {
    dbContext = c;
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    log.debug("Received Msg : {}",  msg.getClass().getName());
    Command ret = null;
    try {
      if (msg instanceof AddMessage) {
        AddMessage am = (AddMessage) msg;
        ret = execute(new Add(am.getPath()), am.getInput(), null);
        getSender().tell(new ResultMessage(true, ret.info(), null), getSelf());
      } else if (msg instanceof CreateMessage) {
        CreateMessage cm = (CreateMessage) msg;
        ret = execute(new CreateDB(cm.getName()), cm.getInput(), null);
        getSender().tell(new ResultMessage(true, ret.info(), null), getSelf());
      } else if (msg instanceof ReplaceMessage) {
        ReplaceMessage rm = (ReplaceMessage) msg;
        ret = execute(new Replace(rm.getPath()), rm.getInput(), null);
        getSender().tell(new ResultMessage(true, ret.info(), null), getSelf());
      } else if (msg instanceof StoreMessage) {
        StoreMessage sm = (StoreMessage) msg;
        ret = execute(new Store(sm.getPath()), sm.getInput(), null);
        getSender().tell(new ResultMessage(true, ret.info(), null), getSelf());
      } else if (msg instanceof CommandMessage) {
        Command cmd = new CommandParser(((CommandMessage) msg).getCommand(),
            dbContext).parseSingle();
        ByteStringBuilder bb = new ByteStringBuilder();
        ret = execute(cmd, null, bb.asOutputStream());
        getSender().tell(new ResultMessage(true, ret.info(),
            bb.result().decodeString(CharsetUtil.UTF_8.name())), getSelf());
      } else if (msg instanceof NewQueryMessage) {
        ActorRef q = getContext().actorOf(QueryActor.mkProps(((NewQueryMessage) msg).getQuery(), dbContext));
        getSender().tell(q, getSelf());
      } else if (msg instanceof AssociationErrorEvent) {
        // The remote endpoint is disconnected
        log.info("Remote endpoint at {} is disconnected.", getSender().toString());
        getContext().stop(getSelf());
      }
  
    } catch (final Exception ex) {
      getSender().tell(new BaseXException(ex.getMessage()), getSelf());
    }
  }

  /**
   * Executes the specified command.
   * @param cmd command to be executed
   * @param in input stream
   * @param out output stream
   * @return executed command
   * @throws IOException I/O exception
   */
  private Command execute(final Command cmd, final InputStream in, final OutputStream out) throws IOException {
    // log.info(cmd + " [...]");
    if (in != null)
      cmd.setInput(in);
    if (out == null)
      cmd.execute(dbContext);
    else
      cmd.execute(dbContext, out);
    return cmd;
  }
}