package org.basex.test.server;

import java.io.*;

import org.basex.*;
import org.basex.io.in.*;
import org.basex.server.*;
import org.basex.server.messages.auth.*;
import org.basex.server.messages.command.*;
import org.junit.*;

import com.typesafe.config.*;

import akka.actor.*;
import akka.event.*;
import akka.testkit.*;
import akka.util.*;

/**
 * This class tests basic server operations and the correctness of actor
 * operations on the server side.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class ClientTest extends TestKit {
  /** Server actor system. */
  private static ActorSystem _system = ActorSystem.create("BaseXClient", ConfigFactory
      .load().getConfig("client"));
  /** Logging handler. */
  private LoggingAdapter log = Logging.getLogger(_system, this);
  /** BaseX server instance. */
  private static BaseXAServer server;
  
  /**
   * Default constructor
   */
  public ClientTest() {
    super(_system);
  }
  
  /** Start the server. 
   * @throws IOException I/O exception */
  @BeforeClass
  public static void startServer() throws IOException {
    server = new BaseXAServer();
  }
  
  /** Start a client with successful login. */
  @Test
  public void startClientActor() {
    ActorRef actorRef = _system.actorOf(ClientActor.mkProps("admin", "admin",
        "127.0.0.1", 1984));
    actorRef.tell(new LoginMessage(), super.testActor());
    expectMsg(true);
  }
  
  /** Start a client with wrong login information. */
  @Test
  public void startClientActorLoginFail() {
    ActorRef actorRef = _system.actorOf(ClientActor.mkProps("admin", "wrong",
        "127.0.0.1", 1984));
    actorRef.tell(new LoginMessage(), super.testActor());
    expectMsg(false);
  }
  
  /** Start a client with successful login. 
   * @throws IOException */
  @Test
  public void add() throws IOException {
    ActorRef actorRef = _system.actorOf(ClientActor.mkProps("admin", "admin",
        "127.0.0.1", 1984));
    actorRef.tell(new LoginMessage(), super.testActor());
    expectMsg(true);
    InputStream in = new ArrayInput("<X/>");
    ByteStringBuilder builder = new ByteStringBuilder();
    byte b;
    while ((b = (byte) in.read()) != -1) {
      builder.putByte(b);
    }
    actorRef.tell(new AddMessage("path", builder.result()), super.testActor());
    expectMsgClass(ResultMessage.class);
  }
}
