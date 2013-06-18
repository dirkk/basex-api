package org.basex.server;

import java.io.*;

import org.basex.core.*;

import akka.actor.*;
import akka.event.*;
import akka.io.Tcp.*;

/**
 * An actor, handling query instances on the server side.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Dirk Kirsten
 */
public class QueryHandler extends UntypedActor {
  /** Query ID. */
  private final int id;
  /** Database context. */
  private final Context dbContext;
  /** Logging adapter. */
  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  /** Query Processor. */
  private QueryListener qp;
  
  /**
   * Create Props for the client handler actor.
   * @param ctx database context
   * @param i query id
   * @return Props for creating this actor, can be further configured
   */
  public static Props mkProps(final Context ctx, final int i) {
    return Props.create(QueryHandler.class, ctx, i);
  }
  
  /**
   * Constructor
   * @param ctx database context
   * @param i query id
   */
  public QueryHandler(final Context ctx, final int i) {
    dbContext = ctx;
    id = i;
  }
  
  @Override
  public void onReceive(Object msg) throws Exception {
    if (msg instanceof Received) {
      Received recv = (Received) msg;
      Reader reader = new Reader(recv.data());
      
      // get the command byte
      ServerCmd sc = ServerCmd.get(reader.getByte());
      // skip the query id
      if (sc != ServerCmd.QUERY) {
        reader.getString();
      }
      
      try {
        if (sc == ServerCmd.QUERY) {
          newQuery(reader);
        } else if (sc == ServerCmd.BIND){
          bind(reader);
        } else if (sc == ServerCmd.CONTEXT){
          context(reader);
        } else if (sc == ServerCmd.ITER){
          results();
        } else if (sc == ServerCmd.EXEC){
          exec();
        } else if (sc == ServerCmd.FULL){
          full();
        } else if (sc == ServerCmd.INFO){
          info();
        } else if (sc == ServerCmd.OPTIONS){
          options();
        } else if (sc == ServerCmd.UPDATING){
          updating();
        } else if (sc == ServerCmd.CLOSE){
          close();
        }
      } catch(final IOException ex) {
        new Writer()
          .writeTerminator()
          .writeString(ex.getMessage())
          .send(getSender(), getSelf());
      }
    } else {
      unhandled(msg);
    }
  }

  /**
   * A client wants to create a new query instance.
   *
   * @param reader incoming message reader
   */
  private void newQuery(final Reader reader) {
    try {
      final String query = reader.getString();
      qp = new QueryListener(query, dbContext);
      // write log file
      log.info("Query: {}", query);
      
      // send {ID}0
      // send 0 as success flag
      new Writer()
        .writeString(String.valueOf(id))
        .writeSuccess(true)
        .send(getSender(), getSelf());

    } catch(final Throwable ex) {
      log.error("New Query Error: {}", ex.getMessage());
    }
  }
  
  /**
   * Binds a value to a global variable.
   * @param reader incoming message reader
   * @throws IOException I/O exception
   */
  private void bind(final Reader reader) throws IOException {
    final String key = reader.getString();
    final String val = reader.getString();
    final String typ = reader.getString();
    
    qp.bind(key, val, typ);
    new Writer().writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Binds a value to the context item.
   * @param reader incoming message reader
   * @throws IOException I/O exception
   */
  private void context(final Reader reader) throws IOException {
    final String val = reader.getString();
    final String typ = reader.getString();
    
    qp.context(val, typ);
    new Writer().writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Sends the single items as strings, prefixed by a single byte (\x) that
   * represents the Type ID. This command is called by the more() function
   * of a client implementation.
   * @throws IOException I/O exception
   */
  private void results() throws IOException {
    Writer w = new Writer();
    qp.execute(true, w.getOutputStream(), true, false);
    w.writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Executes the query and sends all results as a single string.
   * @throws IOException I/O exception
   */
  private void exec() throws IOException {
    Writer w = new Writer();
    qp.execute(false, w.getOutputStream(), true, false);
    w.writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Returns all resulting items as strings, prefixed by the XDM Meta Data.
   * This command is e. g. used by the XQJ API.
   * @throws IOException I/O exception
   */
  private void full() throws IOException {
    Writer w = new Writer();
    qp.execute(true, w.getOutputStream(), true, true);
    w.writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Sends the query info.
   */
  private void info() {
    new Writer().writeString(qp.info())
        .writeTerminator().send(getSender(), getSelf());
  }

  /**
   * Sends the serialization options.
   * @throws IOException I/O Exception
   */
  private void options() throws IOException {
    new Writer().writeString(qp.options()).writeTerminator()
      .send(getSender(), getSelf());
  }

  /**
   * Sends {@code true} if the query may perform updates.
   * @throws IOException I/O Exception
   */
  private void updating() throws IOException {
    new Writer().writeString(Boolean.toString(qp.updating()))
      .writeTerminator().send(getSender(), getSelf());
  }

  /**
   * Closes the query and stops this actor.
   */
  private void close() {
    new Writer().writeTerminator().writeTerminator()
      .send(getSender(), getSelf());
    getContext().stop(getSelf());
  }
}
