package io.vertx.redis.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.redis.RedisService;

import java.nio.charset.Charset;

public abstract class AbstractRedisService implements RedisService {

  private static final Logger log = LoggerFactory.getLogger(AbstractRedisService.class);

  private enum ResponseTransform {
    NONE,
    ARRAY_TO_OBJECT,
    INFO
  }

  final Vertx vertx;
  final JsonObject config;
  final EventBus eb;

  private RedisConnection redisClient;
  private RedisSubscriptions subscriptions = new RedisSubscriptions();

  private String encoding;
  private Charset charset;
  private String baseAddress;

  AbstractRedisService(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.eb = vertx.eventBus();
  }

  @Override
  public void start(final Handler<AsyncResult<Void>> handler) {
    final String host = config.getString("host", "localhost");
    final int port = config.getInteger("port", 6379);
    final String enc = config.getString("encoding", "UTF-8");
    final boolean binary = config.getBoolean("binary", false);

    if (binary) {
      log.warn("Binary mode is not implemented yet!!!");
    }

    encoding = enc;
    charset = Charset.forName(enc);
    baseAddress = config.getString("address", "io.vertx.mod-redis");

    redisClient = new RedisConnection(vertx, host, port, subscriptions);
    redisClient.connect(handler);
  }

  @Override
  public void stop(final Handler<AsyncResult<Void>> handler) {
    redisClient.disconnect(handler);
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

    redisClient.send(new Command(command, redisArgs, charset).setExpectedReplies(expectedReplies).setHandler(reply -> {
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
          } else {
            resultHandler.handle(new RedisAsyncResult<>(null, reply.asType(returnType, encoding)));
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
    }));
  }

}

