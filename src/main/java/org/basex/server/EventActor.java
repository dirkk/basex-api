package org.basex.server;

import static org.basex.core.Text.*;

import java.net.*;

import org.basex.util.*;

import akka.actor.*;
import akka.event.*;
import akka.io.*;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;

/**
 * This runs the event server. Clients can connect to this port and actively
 * listen on the TCP/IP connection for an event to be thrown.
 * 
 * This is a rather bad architecture due to the old client/server API. It means
 * opening a new thread for each event to listen to on the client side.
 * 
 * TODO: At the moment, no event notification is ever thrown.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class EventActor extends UntypedActor {
  /** Listening address. */
  private InetSocketAddress addr;
  /** Logging adapter. */
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  /**
   * Create Props for the client handler actor.
   * @param a listening socket address
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps(final InetSocketAddress a) {
    return Props.create(EventActor.class, a);
  }
  
  /**
   * Constructor.
   * @param a listening socket address
   */
  public EventActor(final InetSocketAddress a) {
    addr = a;
  }
  
  @Override
  public void preStart() throws Exception {
    final ActorRef tcp = Tcp.get(getContext().system()).manager();
    tcp.tell(TcpMessage.bind(getSelf(), addr, 100), getSelf());
  }

  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof Bound) {
      Bound b = (Bound) msg;
      log.info("Event Server bound to {} ", b.localAddress());
      Util.outln(CONSOLE + Util.info(SRV_STARTED_PORT_X, b.localAddress().getPort()), SERVERMODE);
    } else if (msg instanceof CommandFailed) {
      getContext().stop(getSelf());
    } else if (msg instanceof Connected) {
      final Connected conn = (Connected) msg;
      log.info("Connection from {}", conn.remoteAddress());
      final ActorRef handler = getContext().actorOf(EventHandler.mkProps());
      getSender().tell(TcpMessage.register(handler), getSelf());
    } else {
      unhandled(msg);
    }
  }
}
