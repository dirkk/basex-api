package org.basex.server;

import static org.basex.core.Text.*;

import java.net.*;

import org.basex.core.*;
import org.basex.util.*;

import akka.actor.*;
import akka.event.*;
import akka.io.*;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;

/**
 * The server actor, responsible for binding to a port at the server side and
 * listening to incoming connections.
 * 
 * Each connection (including the authentication management) will then be passed
 * over to a {@link ClientHandler} actor.
 * 
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class ServerActor extends UntypedActor {
  /** Listening address. */
  private final InetSocketAddress listening;
  /** Logging adapter. */
  private final LoggingAdapter log;
  /** Database context. */
  private final Context dbContext;
  /** Bound listener. */
  private ActorRef boundListener;
  /** Is sucessfully bound. */
  private boolean bound = false;

  /**
   * Create Props for the server actor.
   * @param l listening address for incoming connections
   * @param ctx database context
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps(final InetSocketAddress l, final Context ctx) {
    return Props.create(ServerActor.class, l, ctx);
  }
  
  /**
   * Constructor.
   * @param l listening address
   * @param ctx database context
   */
  public ServerActor(final InetSocketAddress l, final Context ctx) {
    listening = l;
    dbContext = ctx;
    log = Logging.getLogger(getContext().system(), this);
  }
  
  @Override
  public void preStart() throws Exception {
    final ActorRef tcp = Tcp.get(getContext().system()).manager();
    tcp.tell(TcpMessage.bind(getSelf(), listening, 100), getSelf());
    
    getContext().actorOf(EventActor.mkProps(
        new InetSocketAddress(listening.getAddress(), dbContext.mprop.num(dbContext.mprop.EVENTPORT))),
        "events"
        );
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof String && ((String) msg).equalsIgnoreCase("bound")) {
      if (bound)
        getSender().tell(true, getSelf());
      else
        boundListener = getSender();
    } else if (msg instanceof Bound) {
      Bound b = (Bound) msg;

      log.info("Server bound to {} ", b.localAddress());
      Util.outln(CONSOLE + Util.info(SRV_STARTED_PORT_X, b.localAddress().getPort()), SERVERMODE);
      bound = true;
      if (boundListener != null)
        boundListener.tell(true, getSelf());
    } else if (msg instanceof CommandFailed) {
      getContext().stop(getSelf());
    } else if (msg instanceof Connected) {
      final Connected conn = (Connected) msg;
      log.info("Connection from {}", conn.remoteAddress());
      final ActorRef handler = getContext().actorOf(ClientHandler.mkProps(dbContext));
      getSender().tell(TcpMessage.register(handler), getSelf());
      handler.tell("connect", getSender());
    }
  }
}
