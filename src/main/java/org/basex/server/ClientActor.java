package org.basex.server;

import org.basex.server.messages.auth.*;
import org.basex.server.messages.command.*;

import static org.basex.util.Token.md5;

import akka.actor.*;
import akka.event.*;
import akka.japi.*;

/**
 * This class
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class ClientActor extends UntypedActor {
  /** User name. */
  protected final String user;
  /** Hashed Password. */
  protected final String password;
  /** Remote actor. */
  protected ActorRef remoteActor;
  /** Remote address. */
  protected final Address addr;
  /** Logging instance. */
  protected LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  /** A reference to reply to. */
  protected ActorRef reply;


  /**
   * Create a new {@link Props} object, a configuration class, to create
   * a new actor.
   * 
   * @param u user name
   * @param p hashed password
   * @param h host name
   * @param po port
   * @return props
   */
  public static Props mkProps(final String u, final String p, final String h, final int po) {
    return Props.create(ClientActor.class, u, p, h, po);
  }

  /**
   * Default constructor.
   * @param u user name
   * @param p plain text password
   * @param host host name
   * @param port port number
   */
  public ClientActor(final String u, final String p, final String host, final int port) {
    user = u;
    password = md5(p);
    addr = new Address("akka.tcp", "BaseXServer", host, port);
  }

  @Override
  public void onReceive(Object msg) throws Exception {
    log.debug("ClientActor: {}",  msg.getClass().getName());
    
    Procedure<Object> normal = new Procedure<Object>() {
      @Override
      public void apply(Object msg) {
        if(msg instanceof AddMessage || msg instanceof CommandMessage ||
            msg instanceof NewQueryMessage || msg instanceof CreateMessage ||
            msg instanceof ReplaceMessage || msg instanceof StoreMessage) {
          reply = getSender();
          remoteActor.tell(msg, getSelf());
        } else if (msg instanceof ResultMessage) {
          reply.forward(msg, getContext());
        } else if (msg instanceof ActorRef) {
          reply.forward(msg, getContext());
        } else if (msg instanceof Exception) {
          reply.forward(msg, getContext());
        } else {
          unhandled(msg);
        }
      }
    };
    
    if (msg instanceof LoginMessage) {
      reply = getSender();
      ActorSelection sel = getContext().actorSelection(addr.toString() + "/user/server");
      sel.tell(new CredentialsMessage(user, password), getSelf());
    } else if (msg instanceof ActorRef) {
      remoteActor = (ActorRef) msg;
      reply.forward(msg, getContext());
      getContext().become(normal);
    } else {
      unhandled(msg);
    }
  }
}