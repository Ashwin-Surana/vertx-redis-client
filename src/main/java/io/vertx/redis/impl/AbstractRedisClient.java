package io.vertx.redis.impl;

import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;

public abstract class AbstractRedisClient implements RedisClient {

  private static final Logger log = LoggerFactory.getLogger(AbstractRedisClient.class);

  private enum ResponseTransform {
    NONE,
    ARRAY_TO_OBJECT,
    INFO
  }

  private final Vertx vertx;
  private final JsonObject config;
  private final EventBus eb;
  private final RedisSubscriptions subscriptions = new RedisSubscriptions();
  private final Queue<Command<?>> pending = new LinkedList<>();
  private final String encoding;
  private final Charset charset;
  private final String binaryEnc;
  private final Charset binaryCharset;
  private final String baseAddress;

  private RedisConnection redisConnection;

  AbstractRedisClient(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.eb = vertx.eventBus();
    this.encoding = config.getString("encoding", "UTF-8");
    this.charset = Charset.forName(encoding);
    this.binaryEnc = "iso-8859-1";
    this.binaryCharset = Charset.forName(binaryEnc);
    this.baseAddress = config.getString("address", "io.vertx.mod-redis");
    boolean binary = config.getBoolean("binary", false);
    if (binary) {
      log.warn("Binary mode is not implemented yet!!!");
    }
  }

  @Override
  public synchronized void close(Handler<AsyncResult<Void>> handler) {
    if (redisConnection != null) {
      redisConnection.disconnect(handler);
      redisConnection = null;
    } else if (handler != null) {
      vertx.getOrCreateContext().runOnContext(v -> handler.handle(Future.succeededFuture()));
    }
  }

  private ResponseTransform getResponseTransformFor(String command) {
    if (command.equals("HGETALL")) {
      return ResponseTransform.ARRAY_TO_OBJECT;
    }
    if (command.equals("INFO")) {
      return ResponseTransform.INFO;
    }

    return ResponseTransform.NONE;
  }

  final void sendString(final String command, final JsonArray args, final Handler<AsyncResult<String>> resultHandler) {
    send(command, args, String.class, resultHandler);
  }

  final void sendLong(final String command, final JsonArray args, final Handler<AsyncResult<Long>> resultHandler) {
    send(command, args, Long.class, resultHandler);
  }

  final void sendVoid(final String command, final JsonArray args, final Handler<AsyncResult<Void>> resultHandler) {
    send(command, args, Void.class, resultHandler);
  }

  final void sendJsonArray(final String command, final JsonArray args, final Handler<AsyncResult<JsonArray>> resultHandler) {
    send(command, args, JsonArray.class, resultHandler);
  }

  final void sendJsonObject(final String command, final JsonArray args, final Handler<AsyncResult<JsonObject>> resultHandler) {
    send(command, args, JsonObject.class, resultHandler);
  }

  @SuppressWarnings("unchecked")
  final <T> void send(final String command, final JsonArray redisArgs,
                      final Class<T> returnType,
                      final Handler<AsyncResult<T>> resultHandler) {

    send(command, redisArgs, returnType, false, resultHandler);
  }

  @SuppressWarnings("unchecked")
  final <T> void send(final String command, final JsonArray redisArgs,
                      final Class<T> returnType,
                      final boolean binary,
                      final Handler<AsyncResult<T>> resultHandler) {

    final ResponseTransform transform = getResponseTransformFor(command);

    // subscribe/psubscribe and unsubscribe/punsubscribe commands can have multiple (including zero) replies
    int expectedReplies = 1;

    switch (command) {
      // argument "pattern" ["pattern"...]
      case "psubscribe":
        // in this case we need also to register handlers
        if (redisArgs == null) {
          resultHandler.handle(new RedisAsyncResult<>("at least one pattern is required!"));
          return;
        }
        expectedReplies = redisArgs.size();
        for (Object obj : redisArgs) {
          String pattern = (String) obj;
          // compose the listening address as base + . + pattern
          final String vertxChannel = baseAddress + "." + pattern;
          subscriptions.registerPatternSubscribeHandler(pattern, (pattern1, replyData) -> {
            JsonObject replyMessage = new JsonObject();
            replyMessage.put("status", "ok");
            JsonObject message = new JsonObject();
            message.put("pattern", pattern1);
            message.put("channel", replyData[2].asType(String.class, encoding));
            message.put("message", replyData[3].asType(String.class, encoding));
            replyMessage.put("value", message);
            eb.send(vertxChannel, replyMessage);
          });
        }
        break;
      // argument "channel" ["channel"...]
      case "subscribe":
        if (redisArgs == null) {
          resultHandler.handle(new RedisAsyncResult<>("at least one pattern is required!"));
          return;
        }
        // in this case we need also to register handlers
        expectedReplies = redisArgs.size();
        for (Object obj : redisArgs) {
          String channel = (String) obj;
          // compose the listening address as base + . + channel
          final String vertxChannel = baseAddress + "." + channel;
          subscriptions.registerChannelSubscribeHandler(channel, (channel1, replyData) -> {
            JsonObject replyMessage = new JsonObject();
            replyMessage.put("status", "ok");
            JsonObject message = new JsonObject();
            message.put("channel", channel1);
            message.put("message", replyData[2].asType(String.class, encoding));
            replyMessage.put("value", message);
            eb.send(vertxChannel, replyMessage);
          });
        }
        break;
      // argument ["pattern" ["pattern"...]]
      case "punsubscribe":
        // unregister all channels
        if (redisArgs == null || redisArgs.size() == 0) {
          // unsubscribe all
          expectedReplies = subscriptions.patternSize();
          subscriptions.unregisterPatternSubscribeHandler(null);
        } else {
          expectedReplies = redisArgs.size();
          for (Object obj : redisArgs) {
            String pattern = (String) obj;
            subscriptions.unregisterPatternSubscribeHandler(pattern);
          }
        }
        break;
      // argument ["channel" ["channel"...]]
      case "unsubscribe":
        // unregister all channels
        if (redisArgs == null || redisArgs.size() == 0) {
          // unsubscribe all
          expectedReplies = subscriptions.channelSize();
          subscriptions.unregisterChannelSubscribeHandler(null);
        } else {
          expectedReplies = redisArgs.size();
          for (Object obj : redisArgs) {
            String channel = (String) obj;
            subscriptions.unregisterChannelSubscribeHandler(channel);
          }
        }
        break;
    }

    Context context = vertx.getOrCreateContext();

    Command<T> comm = new Command<T>(context, command, redisArgs, binary ? binaryCharset : charset)
                     .setExpectedReplies(expectedReplies).setHandler((Reply reply) -> {
        switch (reply.type()) {
          case '-': // Error
            resultHandler.handle(new RedisAsyncResult<>(reply.asType(String.class)));
            return;
          case '+':   // Status
            resultHandler.handle(new RedisAsyncResult<>(null, reply.asType(returnType)));
            return;
          case '$':  // Bulk
            if (transform == ResponseTransform.INFO) {
              String info = reply.asType(String.class, encoding);

              if (info == null) {
                resultHandler.handle(new RedisAsyncResult<>(null, null));
              } else {
                String lines[] = info.split("\\r?\\n");
                JsonObject value = new JsonObject();

                JsonObject section = null;
                for (String line : lines) {
                  if (line.length() == 0) {
                    // end of section
                    section = null;
                    continue;
                  }

                  if (line.charAt(0) == '#') {
                    // begin section
                    section = new JsonObject();
                    // create a sub key with the section name
                    value.put(line.substring(2).toLowerCase(), section);
                  } else {
                    // entry in section
                    int split = line.indexOf(':');
                    if (section == null) {
                      value.put(line.substring(0, split), line.substring(split + 1));
                    } else {
                      section.put(line.substring(0, split), line.substring(split + 1));
                    }
                  }
                }
                resultHandler.handle(new RedisAsyncResult<>(null, (T) value));
              }
            } else {
              resultHandler.handle(new RedisAsyncResult<>(null, reply.asType(returnType, binary ? binaryEnc : encoding)));
            }
            return;
          case '*': // Multi
            if (transform == ResponseTransform.ARRAY_TO_OBJECT) {
              resultHandler.handle(new RedisAsyncResult<>(null, (T) reply.asType(JsonObject.class, encoding)));
            } else {
              resultHandler.handle(new RedisAsyncResult<>(null, (T) reply.asType(JsonArray.class, encoding)));
            }
            return;
          case ':':   // Integer
            resultHandler.handle(new RedisAsyncResult<>(null, reply.asType(returnType)));
            return;
          default:
            resultHandler.handle(new RedisAsyncResult<>("Unknown message type"));
        }
      });
    comm.setUserHandler(resultHandler);

    doSend(comm);
  }

  private synchronized void doSend(Command command) {
    if (redisConnection == null) {
      pending.add(command);
      connect();
    } else {
      sendOnConnection(command);
    }
  }

  private void connect() {
    final String host = config.getString("host", "localhost");
    final int port = config.getInteger("port", 6379);
    RedisConnection conn = new RedisConnection(vertx, host, port, subscriptions, v -> connectionClosed());
    conn.connect(res -> {
      if (res.succeeded()) {
        connected(conn);
      } else {
        sendPendingFailed(res.cause());
      }
    });
  }

  private synchronized void connectionClosed() {
    if (redisConnection != null) {
      redisConnection = null;
      log.warn("Connection has been closed by peer");
    }
  }

  private synchronized void connected(RedisConnection conn) {
    this.redisConnection = conn;
    Command command;
    while ((command = pending.poll()) != null) {
      sendOnConnection(command);
    }
  }

  @SuppressWarnings("unchecked")
  private synchronized void sendPendingFailed(Throwable cause) {
    Command command;
    while ((command = pending.poll()) != null) {
      Handler<AsyncResult<Object>> userHandler = command.getUserHandler();
      if (userHandler != null) {
        command.getContext().runOnContext(aVoid -> {
          try {
            userHandler.handle(Future.failedFuture(cause));
          } catch (Throwable t) {
            log.error("Failure in user handler", t);
          }
        });
      }
    }
  }

  private void sendOnConnection(Command command) {
    redisConnection.send(command);
  }

}

