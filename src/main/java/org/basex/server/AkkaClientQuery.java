package org.basex.server;

import java.io.*;

import org.basex.core.*;
import org.basex.server.messages.command.*;
import org.jboss.netty.util.*;

import scala.concurrent.*;

import akka.actor.*;
import akka.pattern.*;
import akka.util.Timeout;

/**
 * This class defines all methods for iteratively evaluating queries with the
 * client/server architecture. All sent data is received by the
 * {@link ClientListener} and interpreted by the {@link QueryListener}.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Dirk Kirsten
 */
public class AkkaClientQuery extends Query {
  /** Client session. */
  protected final AkkaClientSession cs;
  /** Query id. */
  protected final ActorRef actor;
  /** Timeout. */
  protected final Timeout timeout;

  /**
   * Standard constructor.
   * @param query query to be run
   * @param session client session
   * @param os output stream
   * @throws IOException I/O exception
   */
  public AkkaClientQuery(final String query, final AkkaClientSession session,
      final OutputStream os) throws IOException {
    // create the client
    timeout = new Timeout(30000);
    Future<Object> future = Patterns.ask(session.getCommandActor(), new NewQueryMessage(query), timeout);
    try {
      actor = (ActorRef) Await.result(future, timeout.duration());
    } catch(Exception e) {
      throw new BaseXException(e);
    }
    
    cs = session;
    out = os;
  }

  @Override
  public String info() throws IOException {
    ResultMessage rm = (ResultMessage) sendAndWait(ServerCmd.INFO);
    return rm.getResult();
  }

  @Override
  public String options() throws IOException {
    ResultMessage rm = (ResultMessage) sendAndWait(ServerCmd.OPTIONS);
    return rm.getResult();
  }

  @Override
  public boolean updating() throws IOException {
    ResultMessage rm = (ResultMessage) sendAndWait(ServerCmd.UPDATING);
    return Boolean.parseBoolean(rm.getResult());
  }

  @Override
  public void bind(final String n, final Object v, final String t) throws IOException {
    send(new BindMessage(n, v.toString(), t));
  }

  @Override
  public void context(final Object v, final String t) throws IOException {
    send(new ContextMessage(v.toString(), t));
  }

  @Override
  public String execute() throws IOException {
    ResultMessage rm = (ResultMessage) sendAndWait(ServerCmd.EXEC);
    return rm.getResult();
  }

  @Override
  public void close() throws IOException {
    send(ServerCmd.CLOSE);
  }

  @Override
  protected void cache() throws IOException {
    ResultMessage rm = (ResultMessage) sendAndWait(ServerCmd.ITER);
    cache(new ByteArrayInputStream(rm.getResult().getBytes(CharsetUtil.UTF_8)));
  }
  
  /**
   * Send a message to the remote processing actor and wait for the result.
   * 
   * Blocks until a result arrived or the timeout was reached.
   * 
   * @param msg message to send
   * @return result
   * @throws IOException I/O exception
   */
  private Object sendAndWait(Object msg) throws IOException {
    Future<Object> future = Patterns.ask(actor, msg, timeout);
    Object result;
    try {
      result = Await.result(future, timeout.duration());    
    } catch(Exception e) {
      throw new BaseXException(e);
    }
    if (result instanceof BaseXException)
      throw (BaseXException) result;
    
    return result;
  }
  
  /**
   * Send a message to the remote processing actor.
   * 
   * @param msg message to send
   */
  private void send(Object msg) {
    actor.tell(msg, null);
  }
}
