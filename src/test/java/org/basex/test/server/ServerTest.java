package org.basex.test.server;

import org.basex.core.*;
import org.basex.server.*;
import org.basex.server.messages.auth.*;
import org.junit.*;

import com.typesafe.config.*;

import akka.actor.*;
import akka.event.*;
import akka.testkit.*;

/**
 * This class tests basic server operations and the correctness of actor
 * operations on the server side.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class ServerTest extends TestKit {
  /** Server actor system. */
  static ActorSystem _system = ActorSystem.create("BaseXServer", ConfigFactory
      .load().getConfig("server"));
  /** Logging handler. */
  LoggingAdapter log = Logging.getLogger(_system, this);
  
  /**
   * Default constructor
   */
  public ServerTest() {
    super(_system);
  }
  
  /** Start a server actor. */
  @Test
  public void startServerActor() {
    ActorRef actorRef = _system.actorOf(LoginActor.mkProps(new Context()));
    actorRef.tell(new LoginMessage(), super.testActor());
    expectMsgClass(TimestampMessage.class);
  }
}
