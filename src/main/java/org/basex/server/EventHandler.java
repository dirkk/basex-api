package org.basex.server;

import org.basex.core.*;

import akka.actor.*;
import akka.event.*;
import akka.io.Tcp.*;

public class EventHandler extends UntypedActor {
  /** Logging adapter. */
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
