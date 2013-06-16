package org.basex.server;

import java.io.*;

import org.basex.core.*;

import akka.actor.*;
import akka.event.*;
import akka.io.*;
import akka.io.Tcp.*;
import akka.util.*;

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
        ByteStringBuilder bb = new ByteStringBuilder();
        bb.putByte((byte) 0);
        bb.append(ByteString.fromString(ex.getMessage()));
        bb.putByte((byte) 0);
        respond(bb.result());
      }
    }
  }

  private void newQuery(final Reader reader) {
    try {
      final StringBuilder info = new StringBuilder();
      final String query = reader.getString();
      qp = new QueryListener(query, dbContext);
      // write log file
      log.info("Query: {}", query);
      ByteStringBuilder bb = new ByteStringBuilder();
      // send {ID}0
      bb.append(ByteString.fromString(String.valueOf(id)));
      bb.putByte((byte) 0);
      // send 0 as success flag
      bb.putByte((byte) 0);
      respond(bb.result());

    } catch(final Throwable ex) {
      log.error("New Query Error: {}", ex.getMessage());
    }
  }
  
  private void bind(final Reader reader) throws IOException {
    final String key = reader.getString();
    final String val = reader.getString();
    final String typ = reader.getString();
    
    qp.bind(key, val, typ);
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void context(final Reader reader) throws IOException {
    final String val = reader.getString();
    final String typ = reader.getString();
    
    qp.context(val, typ);
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void results() throws IOException {
    ByteStringBuilder bb = new ByteStringBuilder();
    qp.execute(true, bb.asOutputStream(), true, false);
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void exec() throws IOException {
    ByteStringBuilder bb = new ByteStringBuilder();
    qp.execute(false, bb.asOutputStream(), true, false);
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void full() throws IOException {
    ByteStringBuilder bb = new ByteStringBuilder();
    qp.execute(true, bb.asOutputStream(), true, true);
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void info() {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(qp.info()));
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void options() throws IOException {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(qp.options()));
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void updating() throws IOException {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.append(ByteString.fromString(Boolean.toString(qp.updating())));
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
  }

  private void close() {
    ByteStringBuilder bb = new ByteStringBuilder();
    bb.putByte((byte) 0);
    bb.putByte((byte) 0);
    respond(bb.result());
    getContext().stop(getSelf());
  }

  private void respond(final ByteString payload) {
    getSender().tell(TcpMessage.write(payload), getSelf());
  }
  
}
