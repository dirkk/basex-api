package org.basex.server;

import akka.actor.*;
import akka.event.*;
import akka.io.Tcp.*;

/**
 * Handles incoming connections on the event server socket.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class EventHandler extends UntypedActor {
  /** Logging adapter. */
  @SuppressWarnings("unused")
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  
  /**
   * Create Props for the event handler actor.
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps() {
    return Props.create(EventHandler.class);
  }

  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof Received) {
      String name = new Reader(((Received) msg).data()).getString();
      ActorSelection sel = getContext().actorSelection("../../" + name);
      sel.tell(getSelf(), getSender());
    } else {
      unhandled(msg);
    }
  }

}
