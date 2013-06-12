package org.basex.server;

import java.util.concurrent.*;

import org.basex.core.*;
import org.basex.server.messages.auth.*;

import scala.concurrent.duration.*;

import akka.actor.*;
import akka.event.*;
import akka.remote.*;

public class LoginActor extends UntypedActor {
  /** Database context. */
  protected final Context dbContext;
  /** Logging instance. */
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  /**
   * Create a new {@link Props} object, a configuration class, to create
   * a new actor.
   * 
   * @param c database context
   * @return props
   */
  public static Props mkProps(final Context c) {
    return Props.create(LoginActor.class, c);
  }
  
  /**
   * Default constructor.
   * @param c database context
   */
  public LoginActor(final Context c) {
    dbContext = c;
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    log.debug("Received Msg : {}",  msg.getClass().getName());
    if (msg instanceof CredentialsMessage) {
      CredentialsMessage cm = (CredentialsMessage) msg;
      // check if the user + password are correct
      User user = dbContext.users.get(cm.getUser());
      
      if (user != null && user.password.equals(cm.getPassword())) {
        // login successful
        Context newContext = new Context(dbContext, null);
        newContext.user = user;
        ActorRef session = getContext().actorOf(CommandActor.mkProps(newContext));
        getContext().system().eventStream().subscribe(session, AssociationErrorEvent.class);
        // return the successful login result to the client
        getSender().tell(session, getSelf());
      } else {
        // login failed
        log.info("Unsuccessful login attempt from user {}", cm.getUser());
        // send the login result message with a delay of one second
        getContext().system().scheduler().scheduleOnce(
            Duration.create(1, TimeUnit.SECONDS),
            getSender(),
            new LoginResultMessage(false),
            getContext().system().dispatcher(),
            getSelf()
        );
      }
    } else {
      unhandled(msg);
    }
  }
}