package io.vertx.test.redis;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisService;
import io.vertx.test.core.VertxTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import redis.embedded.RedisServer;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * This test relies on a Redis server, by default it will start and stop a Redis server unless
 * the <code>host</code> or <code>port</code> system property is specified. In this case the
 * test assumes an external database will be used.
 */
public class RedisServiceTestBase extends VertxTestBase {

  private static final Integer DEFAULT_PORT = 6379;

  static RedisServer redisServer;

  private static final Map<Integer, RedisServer> instances = new ConcurrentHashMap<>();

  private static String getHost() {
    return getProperty("host");
  }

  private static String getPort() {
    return getProperty("port");
  }

  private static String getProperty(String name) {
    String s = System.getProperty(name);
    return (s != null && s.trim().length() > 0) ?  s : null;
  }

  protected RedisService redis;

  @BeforeClass
  static public void startRedis() throws Exception {
    if (getHost() == null && getPort() == null) {
      createRedisInstance(DEFAULT_PORT);
      instances.get(DEFAULT_PORT).start();
    }
  }

  @AfterClass
  static public void stopRedis() throws Exception {
    for(Map.Entry<Integer, RedisServer> entry: instances.entrySet()) {
      if(entry != null){
        entry.getValue().stop();
      }
    }
  }

  public static void createRedisCount(final int count) throws Exception {
    Integer[] ports = new Integer[count];
    Integer basePort = DEFAULT_PORT;
    for(int i = 0; i < count; i++) {
      ports[i] = basePort++;
    }
    createRedisInstance(ports);
  }

  public static void createRedisInstance(final Integer... ports) throws Exception {
    for(Integer port: ports) {
      instances.put(port, new RedisServer(port));
    }
  }

  protected JsonObject getConfig() {
    JsonObject config = new JsonObject();
    String host = getHost();
    String port = getPort();
    if (host != null) {
      config.put("host", host);
    }
    if (port != null) {
      config.put("port", Integer.parseInt(port));
    }
    return config;
  }

  private static JsonArray toJsonArray(final Object... params) {
    return (params != null) ? new JsonArray(Arrays.asList(params)) : null;
  }

  private static Object[] toArray(final Object... params) {
    return params;
  }

  private static String makeKey() {
    return UUID.randomUUID().toString();
  }


  @Test
  public void testAppend() {
    final String key = makeKey();

    redis.del(toJsonArray(key), reply0 -> {
      assertTrue(reply0.succeeded());

      redis.append(toJsonArray(key, "Hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(5l, reply1.result().longValue());

        redis.append(toJsonArray(key, " World"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(11l, reply2.result().longValue());

          redis.get(toJsonArray(key), reply3 -> {
            assertTrue(reply3.succeeded());
            assertTrue(reply3.succeeded());
            assertEquals("Hello World", reply3.result());
            testComplete();
          });
        });
      });
    });

    await();
  }

  @Test
  //Note the try/finally is to ensure that the server is shutdown so other tests do not have to
  //provide auth information
  public void testAuth() throws Exception {

    RedisServer server = RedisServer.builder().port(6381).setting("requirepass foobar").build();
      server.start();
      JsonObject job = new JsonObject().put("host", "localhost").put("port", 6381);
      RedisService rdx = RedisService.create(vertx, job);

      CountDownLatch latch = new CountDownLatch(1);
      rdx.start(asyncResult -> {
        assertTrue(asyncResult.succeeded());
        latch.countDown();
      });

      awaitLatch(latch);

      rdx.auth(new JsonArray().add("barfoo"), reply -> {
        assertFalse(reply.succeeded());
        rdx.auth(new JsonArray().add("foobar"), reply2 -> {
          assertTrue(reply2.succeeded());
          try{
            server.stop();            
          }catch(Exception ignore){}
          testComplete();
        });
      });
      await();    
  }

  @Test
  public void testBgrewriteaof() {
    redis.bgrewriteaof(reply ->{
      assertTrue(reply.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testBgsave() {

    redis.bgsave(reply ->{
      assertTrue(reply.succeeded());
      assertEquals("Background saving started", reply.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testBitcount() {
    final String key = makeKey();

    redis.set(toJsonArray(key, "foobar"), reply0 -> {
      assertTrue(reply0.succeeded());

      redis.bitcount(toJsonArray(key), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(26, reply1.result().longValue());

        redis.bitcount(toJsonArray(key, 0, 0), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(4, reply2.result().longValue());

          redis.bitcount(toJsonArray(key, 1, 1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(6, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testBitop() {
    final String key1 = makeKey();
    final String key2 = makeKey();
    final String destkey = makeKey();

    redis.set(toJsonArray(key1, "foobar"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.set(toJsonArray(key2, "abcdef"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.bitop(toJsonArray("and", destkey, key1, key2), reply2 -> {
          assertTrue(reply2.succeeded());
          redis.get(toJsonArray(destkey), reply3 -> {
            assertTrue(reply3.succeeded());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testBlpop() {
    final String list1 = makeKey();
    final String list2 = makeKey();

    redis.del(toJsonArray(list1, list2), reply0 -> {
      assertTrue(reply0.succeeded());

      redis.rpush(toJsonArray(list1, "a", "b", "c"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(3, reply1.result().longValue());

        redis.blpop(toJsonArray(list1, list2, 0), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray(list1, "a"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testBrpop() {
    final String list1 = makeKey();
    final String list2 = makeKey();

    redis.del(toJsonArray(list1, list2), reply0 -> {
      assertTrue(reply0.succeeded());

      redis.rpush(toJsonArray(list1, "a", "b", "c"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(3, reply1.result().longValue());

        redis.brpop(toJsonArray(list1, list2, 0), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray(list1, "c"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testBrpoplpush() throws Exception{

    JsonArray args = new JsonArray().add("list1").add("list2").add(100);
    redis.brpoplpush(args, result ->{

      if(result.succeeded()){
        redis.lpop(new JsonArray().add("list2"), result2 ->{
          if(result2.succeeded()){
            System.out.println(result2.result());
            assertTrue("hello".equals(result2.result()));
          }
          testComplete();
        });
      }
    });

    RedisService redis2 = RedisService.create(vertx, getConfig());
    CountDownLatch latch = new CountDownLatch(1);
    redis2.start(asyncResult -> {
      if (asyncResult.succeeded()) {
        latch.countDown();
      } else {
        throw new RuntimeException("failed to setup", asyncResult.cause());
      }
    });

    awaitLatch(latch);

    JsonArray args2 = new JsonArray().add("list1").add("hello");
    redis2.lpush(args2, result -> {
    });

    await();

  }

  @Test
  public void testClientKill() {

    redis.clientList(reply -> {
      assertTrue(reply.succeeded());
      String clients = reply.result();
      String add = clients.split("\\s")[0].split("=")[1];
      redis.clientKill(toJsonArray(add), reply2 ->{
        assertTrue(reply2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testClientList() {

    redis.clientList(result -> {
      assertTrue(result.succeeded());
      assertNotNull(result.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testClientSetAndGetName() throws Exception{

    CountDownLatch clientLatch = new CountDownLatch(1);

    redis.clientGetname(result -> {

      if(result.succeeded()) {
        assertNull(result.result());
      }
      clientLatch.countDown();
    });

    awaitLatch(clientLatch);

    // FIXME - need these to make it pass
    Thread.sleep(100);

    CountDownLatch setLatch = new CountDownLatch(1);
    JsonArray args = new JsonArray();
    args.add("test-connection");
    redis.clientSetname(args, result -> {
      assertTrue(result.succeeded());
      setLatch.countDown();
    });

    awaitLatch(setLatch);

    // FIXME - need these to make it pass
    Thread.sleep(100);

    redis.clientGetname(result -> {
      assertTrue(result.succeeded());
      assertEquals("test-connection", result.result());
      testComplete();
    });

    await();
  }


  @Test
  public void testConfigGet() {
    redis.configGet(toJsonArray("*"), reply -> {
      assertTrue(reply.succeeded());
      assertTrue(reply.result() != null && reply.result().size() > 0);
      testComplete();
    });
    await();
  }

  @Test
  public void testConfigSetAndGet() {

    redis.configSet(new JsonArray().add("dbfilename").add("redis.dump"), reply ->{
      if(reply.succeeded()){
        redis.configGet(new JsonArray().add("dbfilename"), reply2 -> {
          if(reply2.succeeded()){
            assertNotNull(reply2.result().getString(0));
            assertTrue(reply2.result().getString(1).equals("redis.dump"));
            testComplete();
          }
        });
      }
    });

    await();
  }

  @Test
  public void testConfigResetstat() {

    redis.info(new JsonArray(), reply ->{
      assertTrue(reply.succeeded());
      JsonObject result = reply.result().getJsonObject("stats");
      Integer conn = Integer.valueOf(result.getString("total_connections_received"));
      assertTrue(conn > 0);
      redis.configResetstat(reply2 -> {
        assertTrue(reply2.succeeded());
        redis.info(new JsonArray(), reply3 ->{
          assertTrue(reply3.succeeded());
          //Note, this may appear strange, but the embedded server handles stats differently
          //Many are not reset correctly. Here we just test the flow of the COMMANDS
          testComplete();
        });

      });
    });
    await();
  }

  @Test
  public void testDbsize() {

    redis.dbsize(reply-> {
      assertTrue(reply.succeeded());
      Long size = reply.result();
      redis.set(new JsonArray().add("new").add("value"), reply2 ->{
        assertTrue(reply2.succeeded());
        redis.dbsize(reply3 ->{
          assertTrue(reply3.succeeded());
          assert(reply3.result() == size + 1);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  //Per the redis doc, this should not be used by clients, perhaps remove it from the API?
  public void testDebugObject() {
  }

  @Test
  @Ignore
  public void testDebugSegfault() throws Exception {

    RedisServer server = RedisServer.builder().port(6381).build();
    server.start();
    JsonObject job = new JsonObject().put("host", "localhost").put("port", 6381);
    RedisService rdx = RedisService.create(vertx, job);

    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });

    awaitLatch(latch);

    rdx.debugSegfault(reply ->{
      assertTrue(reply.succeeded());
      rdx.info(new JsonArray(), reply2 ->{
        assertFalse(reply2.succeeded());
        try{
          server.stop();          
        }catch(Exception ignore){}
        testComplete();
      });
    });

  }

  @Test
  public void testDecr() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "10"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.decr(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(9, reply1.result().longValue());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDecrby() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "10"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.decrby(toJsonArray(mykey, 5), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(5, reply1.result().longValue());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testDel() {
    final String key1 = makeKey();
    final String key2 = makeKey();
    final String key3 = makeKey();

    redis.set(toJsonArray(key1, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.set(toJsonArray(key2, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.del(toJsonArray(key1, key2, key3), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(2, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testDiscard() {
    
    String key = makeKey();    
    redis.set(toJsonArray(key, 0), reply ->{
      assertTrue(reply.succeeded());
      redis.multi(reply2 ->{
        assertTrue(reply2.succeeded());
        redis.incr(toJsonArray(key), reply3 ->{
          assertTrue(reply3.succeeded());
          redis.discard(reply4 ->{
            assertTrue(reply4.succeeded());
            redis.get(toJsonArray(key), reply5 ->{
              assertTrue(reply5.succeeded());
              assertTrue(Integer.valueOf(reply5.result()) == 0);
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  //    @Test
  //    public void testDump() {
  //        final String mykey = makeKey();
  //
  //        redis.set(j(mykey, 10), reply0 -> {
  //            assertTrue(reply0.succeeded());
  //            redis.dump(j(mykey), reply1 -> {
  //                assertTrue(reply1.succeeded());
  //                try {
  //                    byte[] data = reply1.result().getBytes("ISO-8859-1");
  //
  //                    assertEquals(data[0], (byte) 0);
  //                    assertEquals(data[1], (byte) 0xc0);
  //                    assertEquals(data[2], (byte) '\n');
  //                    assertEquals(data[3], (byte) 6);
  //                    assertEquals(data[4], (byte) 0);
  //                    assertEquals(data[5], (byte) 0xf8);
  //                    assertEquals(data[6], (byte) 'r');
  //                    assertEquals(data[7], (byte) '?');
  //                    assertEquals(data[8], (byte) 0xc5);
  //                    assertEquals(data[9], (byte) 0xfb);
  //                    assertEquals(data[10], (byte) 0xfb);
  //                    assertEquals(data[11], (byte) '_');
  //                    assertEquals(data[12], (byte) '(');
  //                    testComplete();
  //                } catch (UnsupportedEncodingException e) {
  //                    fail(e.getMessage());
  //                }
  //            });
  //        });
  //        await();
  //    }

  @Test
  public void testEcho() {
    redis.echo(toJsonArray("Hello World!"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals("Hello World!", reply0.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testEval() {

    final String key1 = makeKey();
    final String key2 = makeKey();
    redis.eval(toJsonArray("return {KEYS[1],KEYS[2],ARGV[1],ARGV[2]}", 2, key1, key2, "first","second"),
        reply -> {
          assertTrue(reply.succeeded());
          Object r = reply.result();
          assertNotNull(r);
          testComplete();
        });
    await();
  }

  @Test
  public void testEvalsha() {
    String inline = "return 1";
    redis.scriptLoad(new JsonArray().add(inline), reply->{
      assertTrue(reply.succeeded());
      assertNotNull(reply.result());
      redis.evalsha(new JsonArray().add(reply.result()).add(0) , reply2 ->{
        assertTrue(reply2.succeeded());
        testComplete();
      });
    });
    await();

  }

  @Test
  //Note same test as testMulti, kept for consistency
  public void testExec() {
    redis.multi(reply -> {
      assertTrue(reply.succeeded());
      redis.set(new JsonArray().add("multi-key").add("first"), reply2 -> {
        assertTrue(reply2.succeeded());
        redis.set(new JsonArray().add("multi-key2").add("second"), reply3 ->{
          assertTrue(reply3.succeeded());
        });
        redis.get(new JsonArray().add("multi-key"), reply4 ->{
          assertTrue(reply4.succeeded());
          assertTrue("QUEUED".equalsIgnoreCase(reply4.result()));
        });
        redis.exec(reply5 ->{
          assertTrue(reply5.succeeded());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testExists() {
    final String key1 = makeKey();
    final String key2 = makeKey();

    redis.set(toJsonArray(key1, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.exists(toJsonArray(key1), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.exists(toJsonArray(key2), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testExpire() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.expire(toJsonArray(mykey, 10), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.ttl(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(10, reply2.result().longValue());

          redis.set(toJsonArray(mykey, "Hello World"), reply3 -> {
            assertTrue(reply3.succeeded());
            redis.ttl(toJsonArray(mykey), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(-1, reply4.result().longValue());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testExpireat() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.exists(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.expireat(toJsonArray(mykey, 1293840000), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());

          redis.exists(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(0, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testFlushall() {

    String key = makeKey();
    //As per the doc, this never fails
    redis.set(toJsonArray(key, "blah"), reply ->{
      assertTrue(reply.succeeded());
      redis.flushall(reply2 ->{
        assertTrue(reply.succeeded());
        redis.get(toJsonArray(key), reply3 ->{
          assertTrue(reply3.succeeded());
          assertNull(reply3.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testFlushdb() {
    String key = makeKey();
    //As per the doc, this never fails
    redis.set(toJsonArray(key, "blah"), reply ->{
      assertTrue(reply.succeeded());
      redis.flushall(reply2 ->{
        assertTrue(reply.succeeded());
        redis.get(toJsonArray(key), reply3 ->{
          assertTrue(reply3.succeeded());
          assertNull(reply3.result());
          testComplete();
        });
      });
    });
    await();

  }

  @Test
  public void testGet() {
    final String nonexisting = makeKey();
    final String mykey = makeKey();

    redis.get(toJsonArray(nonexisting), reply0 -> {
      assertTrue(reply0.succeeded());
      assertNull(reply0.result());

      redis.set(toJsonArray(mykey, "Hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testGetbit() {
    final String mykey = makeKey();

    redis.setbit(toJsonArray(mykey, 7, 1), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(0, reply0.result().longValue());

      redis.getbit(toJsonArray(mykey, 0), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(0, reply1.result().longValue());

        redis.getbit(toJsonArray(mykey, 7), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());

          redis.getbit(toJsonArray(mykey, 100), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(0, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testGetrange() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "This is a string"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.getrange(toJsonArray(mykey, 0, 3), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("This", reply1.result());

        redis.getrange(toJsonArray(mykey, -3, -1), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("ing", reply2.result());

          redis.getrange(toJsonArray(mykey, 0, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("This is a string", reply3.result());

            redis.getrange(toJsonArray(mykey, 10, 100), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals("string", reply4.result());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testGetset() {
    final String mycounter = makeKey();

    redis.incr(toJsonArray(mycounter), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.getset(toJsonArray(mycounter, "0"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("1", reply1.result());

        redis.get(toJsonArray(mycounter), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("0", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHdel() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "foo"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hdel(toJsonArray(myhash, "field1"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hdel(toJsonArray(myhash, "field2"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHexists() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "foo"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hexists(toJsonArray(myhash, "field1"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hexists(toJsonArray(myhash, "field2"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHget() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "foo"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hget(toJsonArray(myhash, "field1"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("foo", reply1.result());

        redis.hget(toJsonArray(myhash, "field2"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertNull(reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHgetall() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hset(toJsonArray(myhash, "field2", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hgetall(toJsonArray(myhash), reply2 -> {
          assertTrue(reply2.succeeded());
          JsonObject obj = reply2.result();
          assertEquals("Hello", obj.getString("field1"));
          assertEquals("World", obj.getString("field2"));
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHincrby() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field", 5), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hincrby(toJsonArray(myhash, "field", 1), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(6, reply1.result().longValue());

        redis.hincrby(toJsonArray(myhash, "field", -1), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(5, reply2.result().longValue());

          redis.hincrby(toJsonArray(myhash, "field", -10), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(-5, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testHIncrbyfloat() {
    final String mykey = makeKey();

    redis.hset(toJsonArray(mykey, "field", 10.50), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hincrbyfloat(toJsonArray(mykey, "field", 0.1), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("10.6", reply1.result());

        redis.hset(toJsonArray(mykey, "field", 5.0e3), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());

          redis.hincrbyfloat(toJsonArray(mykey, "field", 2.0e2), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("5200", reply3.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testHkeys() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hset(toJsonArray(myhash, "field2", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hkeys(toJsonArray(myhash), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("field1", "field2"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHlen() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hset(toJsonArray(myhash, "field2", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hlen(toJsonArray(myhash), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(2, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHmget() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hset(toJsonArray(myhash, "field2", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hmget(toJsonArray(myhash, "field1", "field2", "nofield"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("Hello", "World", null), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHmset() {
    final String myhash = makeKey();

    redis.hmset(toJsonArray(myhash, "field1", "Hello", "field2", "World"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.hget(toJsonArray(myhash, "field1"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("Hello", reply1.result());
        redis.hget(toJsonArray(myhash, "field2"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("World", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHset() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hget(toJsonArray(myhash, "field1"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("Hello", reply1.result());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testHsetnx() {
    final String myhash = makeKey();

    redis.hsetnx(toJsonArray(myhash, "field", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hsetnx(toJsonArray(myhash, "field", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(0, reply1.result().longValue());

        redis.hget(toJsonArray(myhash, "field"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testHvals() {
    final String myhash = makeKey();

    redis.hset(toJsonArray(myhash, "field1", "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.hset(toJsonArray(myhash, "field2", "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());

        redis.hvals(toJsonArray(myhash), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("Hello", "World"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testIncr() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "10"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.incr(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(11, reply1.result().longValue());

        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("11", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testIncrby() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, "10"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.incrby(toJsonArray(mykey, 5), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(15, reply1.result().longValue());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testIncrbyfloat() {
    final String mykey = makeKey();

    redis.set(toJsonArray(mykey, 10.50), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.incrbyfloat(toJsonArray(mykey, 0.1), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("10.6", reply1.result());

        redis.set(toJsonArray(mykey, 5.0e3), reply2 -> {
          assertTrue(reply2.succeeded());
          redis.incrbyfloat(toJsonArray(mykey, 2.0e2), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("5200", reply3.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testInfo() {
    redis.info(toJsonArray(), reply0 -> {
      assertTrue(reply0.succeeded());
      assertNotNull(reply0.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testKeys() {
    redis.mset(toJsonArray("one", 1, "two", 2, "three", 3, "four", 4), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.keys(toJsonArray("*o*"), reply1 -> {
        assertTrue(reply1.succeeded());
        JsonArray array = reply1.result();
        // this is because there are leftovers from previous tests
        assertTrue(3 <= array.size());

        redis.keys(toJsonArray("t??"), reply2 -> {
          assertTrue(reply2.succeeded());
          JsonArray array2 = reply2.result();
          assertTrue(1 == array2.size());

          redis.keys(toJsonArray("*"), reply3 -> {
            assertTrue(reply3.succeeded());
            JsonArray array3 = reply3.result();
            assertTrue(4 <= array3.size());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLastsave() {
    redis.lastsave(reply0 -> {
      assertTrue(reply0.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testLindex() {
    final String mykey = makeKey();

    redis.lpush(toJsonArray(mykey, "World"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.lpush(toJsonArray(mykey, "Hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());

        redis.lindex(toJsonArray(mykey, 0), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());

          redis.lindex(toJsonArray(mykey, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("World", reply3.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLinsert() {
    final String mykey = makeKey();

    redis.rpush(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());

      redis.rpush(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());

        redis.linsert(toJsonArray(mykey, "before", "World", "There"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testLlen() {
    final String mykey = makeKey();
    redis.lpush(toJsonArray(mykey, "World"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.lpush(toJsonArray(mykey, "Hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.llen(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(2, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testLpop() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.lpop(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("one", reply3.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLpush() {
    final String mykey = makeKey();
    redis.lpush(toJsonArray(mykey, "world"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.lpush(toJsonArray(mykey, "hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.lrange(toJsonArray(mykey, 0, -1), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("hello", "world"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testLpushx() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();

    redis.lpush(toJsonArray(mykey, "World"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.lpushx(toJsonArray(mykey, "Hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.lpushx(toJsonArray(myotherkey, "Hello"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          redis.lrange(toJsonArray(mykey, 0, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            JsonArray array3 = reply3.result();
            assertTrue(2 == array3.size());

            assertTrue("Hello".equals(array3.getString(0)));
            assertTrue("World".equals(array3.getString(1)));
            redis.lrange(toJsonArray(myotherkey, 0, -1), reply4 -> {
              JsonArray array4 = reply4.result();
              assertTrue(0 == array4.size());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLrange() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.lrange(toJsonArray(mykey, 0, 0), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("one", reply3.result().getString(0));
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLrem() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "hello"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "foo"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.rpush(toJsonArray(mykey, "hello"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(4, reply3.result().longValue());
            redis.lrem(toJsonArray(mykey, -2, "hello"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(2, reply4.result().longValue());
              redis.lrange(toJsonArray(mykey, 0, -1), reply5 -> {
                assertTrue(reply5.succeeded());
                assertArrayEquals(toArray("hello", "foo"), reply5.result().getList().toArray());
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLset() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.lset(toJsonArray(mykey, 0, "four"), reply3 -> {
            assertTrue(reply3.succeeded());
            redis.lset(toJsonArray(mykey, -2, "five"), reply4 -> {
              assertTrue(reply4.succeeded());
              redis.lrange(toJsonArray(mykey, 0, -1), reply5 -> {
                assertTrue(reply5.succeeded());
                assertArrayEquals(toArray("four", "five", "three"), reply5.result().getList().toArray());
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testLtrim() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.ltrim(toJsonArray(mykey, 1, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            redis.lrange(toJsonArray(mykey, 0, -1), reply5 -> {
              assertTrue(reply5.succeeded());
              assertArrayEquals(toArray("two", "three"), reply5.result().getList().toArray());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testMget() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();
    redis.set(toJsonArray(mykey1, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.set(toJsonArray(mykey2, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.mget(toJsonArray(mykey1, mykey2, "nonexisting"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("Hello", "World", null), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testMigrate() throws Exception {
    RedisServer server = RedisServer.builder().port(6382).build();
    server.start();
    JsonObject job = new JsonObject().put("host", "localhost").put("port", 6382);
    RedisService rdx = RedisService.create(vertx, job);

    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });

    awaitLatch(latch);
    String key = makeKey();
    redis.set(toJsonArray(key, "migrate"), reply ->{
      assertTrue(reply.succeeded());
      redis.migrate(toJsonArray("localhost", 6382, key, 0, 20000), reply2 ->{
        assertTrue(reply2.succeeded());
        rdx.get(toJsonArray(key), reply3 ->{
          assertTrue(reply3.succeeded());
          assertTrue("migrate".equals(reply3.result()));
          try{
            server.stop();            
          }catch(Exception ignore){}          
          testComplete();
        });
      });
    });
    await();
    rdx.stop(reply ->{
      assertTrue(reply.succeeded());
    });
  }

  @Test
  public void testMonitor() {

    redis.monitor(reply ->{
      assertTrue(reply.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testMove() {

    String key = makeKey();
    redis.set(new JsonArray().add(key).add("moved_key"), reply ->{
      assertTrue(reply.succeeded());
      redis.move(new JsonArray().add(key).add(1), reply2 ->{
        assertTrue(reply2.succeeded());
        assertTrue(new Long(1).equals(reply2.result()));
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testMset() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();
    redis.mset(toJsonArray(mykey1, "Hello", mykey2, "World"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.get(toJsonArray(mykey1), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("Hello", reply1.result());
        redis.get(toJsonArray(mykey2), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("World", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testMsetnx() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();
    final String mykey3 = makeKey();

    redis.msetnx(toJsonArray(mykey1, "Hello", mykey2, "there"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.msetnx(toJsonArray(mykey2, "there", mykey3, "world"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(0, reply1.result().longValue());
        redis.mget(toJsonArray(mykey1, mykey2, mykey3), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("Hello", "there", null), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testMulti() throws Exception {

    String key = makeKey();
    RedisService rdx = RedisService.create(vertx, getConfig());
    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });

    awaitLatch(latch);
    
    redis.set(toJsonArray(key, 0), reply ->{
      assertTrue(reply.succeeded());
      
    });
    
    redis.multi(reply -> {
      assertTrue(reply.succeeded());
      redis.set(new JsonArray().add(makeKey()).add(0), reply2 -> {
        assertTrue(reply2.succeeded());
        redis.set(new JsonArray().add(makeKey()).add(0), reply3 ->{
          assertTrue(reply3.succeeded());
        });
        redis.exec(reply4 ->{
          assertTrue(reply4.succeeded());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testObject() {

    String mykey = makeKey();
    redis.set(toJsonArray(mykey, "test"), reply ->{
      assertTrue(reply.succeeded());
      redis.object(toJsonArray("REFCOUNT", mykey), reply2 ->{
        assertTrue(reply2.succeeded());
        redis.object(toJsonArray("ENCODING", mykey), reply3 ->{
          assertTrue(reply3.succeeded());
          redis.object(toJsonArray("IDLETIME", mykey), reply4 ->{
            assertTrue(reply4.succeeded());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testPersist() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.expire(toJsonArray(mykey, 10), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.ttl(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(10, reply2.result().longValue());
          redis.persist(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.ttl(toJsonArray(mykey), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(-1, reply4.result().longValue());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testPexpire() {    
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.pexpire(toJsonArray(mykey, 1000), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());                
        redis.get(toJsonArray(mykey), reply2 -> {
            assertTrue(reply2.succeeded());
            testComplete();
          });
        });
      });
    await();
  }

  @Test
  public void testPexpireat() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.pexpireat(toJsonArray(mykey, 1555555555005l), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.ttl(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertTrue(200000000 > reply2.result() && reply2.result() > 0);
          redis.pttl(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertTrue(1555555555005l > reply3.result() && reply3.result() > 0);
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testPing() {
    redis.ping(reply0 -> {
      assertEquals("PONG", reply0.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testPsetex() {
    final String mykey = makeKey();
    redis.psetex(toJsonArray(mykey, 2000, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.pttl(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertTrue(3000 > reply1.result() && reply1.result() > 0);
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testPubSub() {

    redis.subscribe(new JsonArray().add("rustic"), sub->{

    });
  }
  @Test
  @Ignore
  public void testPsubscribe() {
  }

  @Test
  public void testPttl() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.expire(toJsonArray(mykey, 3), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.pttl(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertTrue(3000 >= reply2.result() && reply2.result() > 0);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testPublish() {
    String key = makeKey();
    redis.set(toJsonArray(key, 0), reply ->{
      assertTrue(reply.succeeded());
      redis.publish(toJsonArray(key, 1), reply2 ->{
        assertTrue(reply2.succeeded());
        assertTrue(reply2.result() == 0);
        testComplete();
      });
    });  
    await();
  }

  @Test
  @Ignore
  public void testPunsubscribe() {
  }

  @Test
  @Ignore
  public void testQuit() {

    redis.quit(reply -> {
      if(reply.succeeded()){
        vertx.setTimer(500, tid -> {
          redis.ping(reply2 ->{
            if(reply2.succeeded()){
              fail("Connection is closed.");
            }
            testComplete();
          });
        });
      }
    });
    await();
  }

  @Test
  public void testRandomkey() {

    redis.set(new JsonArray().add("foo").add("bar"), reply->{
      if(reply.succeeded()){
        redis.randomkey(reply2->{
          if(reply2.succeeded()){
            assertNotNull(reply2.result());
            testComplete();
          }
        });
      }
    });
    await();
  }

  @Test
  public void testRename() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();

    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.rename(toJsonArray(mykey, myotherkey), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.get(toJsonArray(myotherkey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testRenamenx() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();

    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.set(toJsonArray(myotherkey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.renamenx(toJsonArray(mykey, myotherkey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          redis.get(toJsonArray(myotherkey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("World", reply3.result());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  @Ignore
  public void testRestore() {
  }

  @Test
  public void testRpop() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.rpop(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("three", reply3.result());
            redis.lrange(toJsonArray(mykey, 0, -1), reply5 -> {
              assertTrue(reply5.succeeded());
              assertArrayEquals(toArray("one", "two"), reply5.result().getList().toArray());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testRpoplpush() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();
    redis.rpush(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpush(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(3, reply2.result().longValue());
          redis.rpoplpush(toJsonArray(mykey, myotherkey), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("three", reply3.result());
            redis.lrange(toJsonArray(mykey, 0, -1), reply5 -> {
              assertTrue(reply5.succeeded());
              assertArrayEquals(toArray("one", "two"), reply5.result().getList().toArray());
              redis.lrange(toJsonArray(myotherkey, 0, -1), reply6 -> {
                assertArrayEquals(toArray("three"), reply6.result().getList().toArray());
                testComplete();
              });
            });
          });
        });
      });
    }

    );

    await();

  }

  @Test
  public void testRpush() {
    final String mykey = makeKey();
    redis.rpush(toJsonArray(mykey, "hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpush(toJsonArray(mykey, "world"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.lrange(toJsonArray(mykey, 0, -1), reply2 -> {
          assertTrue(reply2.succeeded());
          assertArrayEquals(toArray("hello", "world"), reply2.result().getList().toArray());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testRpushx() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();
    redis.rpush(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.rpushx(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(2, reply1.result().longValue());
        redis.rpushx(toJsonArray(myotherkey, "World"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          redis.lrange(toJsonArray(mykey, 0, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertArrayEquals(toArray("Hello", "World"), reply3.result().getList().toArray());
            redis.lrange(toJsonArray(myotherkey, 0, -1), reply4 -> {
              assertArrayEquals(new Object[0], reply4.result().getList().toArray());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSadd() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey, "World"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          redis.smembers(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            Object[] expected = new Object[]{"Hello", "World"};
            Object[] result = reply3.result().getList().toArray();
            Arrays.sort(result);
            assertArrayEquals(expected, result);
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSave() {
    redis.save(reply ->{
      assertTrue(reply.succeeded());
      //Note, there's really not much else to do
      testComplete();
    });
    await();
  }

  @Test
  public void testScard() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.scard(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(2, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testScriptexists() {
    String inline = "return 1";
    redis.scriptLoad(new JsonArray().add(inline), reply ->{
      assertTrue(reply.succeeded());
      String hash = reply.result();
      redis.scriptExists(new JsonArray().add(hash), reply2 ->{
        assertTrue(reply2.succeeded());
        assertTrue(reply2.result().getInteger(0) > 0);
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testScriptflush() {
    String inline = "return 1";
    redis.scriptLoad(new JsonArray().add(inline), reply ->{
      assertTrue(reply.succeeded());
      String hash = reply.result();
      redis.scriptExists(new JsonArray().add(hash), reply2 ->{
        assertTrue(reply2.succeeded());
        assertTrue(reply2.result().getInteger(0) > 0);
        redis.scriptFlush(reply3 ->{
          assertTrue(reply3.succeeded());
          redis.scriptExists(new JsonArray().add(hash), reply4 ->{
            assertTrue(reply4.succeeded());
            assertTrue(reply4.result().getInteger(0) == 0);
            testComplete();
          });
        });
      });
    });
    await();
  }

  //@Test
  public void testScriptkill() throws Exception {

    String inline = "while true do end";
    redis.eval(new JsonArray().add(inline).add(0), reply ->{
      //server should be locked at this point
    });

    JsonObject job = new JsonObject().put("host", "localhost").put("port", 6379);
    RedisService rdx = RedisService.create(vertx, job);

    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });

    awaitLatch(latch);

    rdx.scriptKill(reply ->{
      assertTrue(reply.succeeded());
      rdx.info(new JsonArray(), reply2 ->{
        assertTrue(reply2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testScriptload() {
    String inline = "return 1";
    redis.scriptLoad(new JsonArray().add(inline), reply->{
      assertTrue(reply.succeeded());
      assertNotNull(reply.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testSdiff() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(1, reply5.result().longValue());
                redis.sdiff(toJsonArray(mykey1, mykey2), reply6 -> {
                  assertTrue(reply6.succeeded());
                  Object[] expected = new Object[]{"a", "b"};
                  Object[] result = reply6.result().getList().toArray();
                  Arrays.sort(result);
                  assertArrayEquals(expected, result);
                  testComplete();
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSdiffstore() {
    final String mykey = makeKey();
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(1, reply5.result().longValue());
                redis.sdiffstore(toJsonArray(mykey, mykey1, mykey2), reply6 -> {
                  assertTrue(reply6.succeeded());
                  Long diff = reply6.result().longValue();
                  assertTrue(diff == 2);
                  redis.smembers(new JsonArray().add(mykey), reply7 ->{
                    Object[] expected = new Object[]{"a", "b"};
                    JsonArray members = reply7.result();
                    Object[] result = members.getList().toArray();
                    assertArrayEquals(expected, result);
                    testComplete();
                  });
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSelect() {
    //Gee, think redis should have a get current DB command?
    redis.select(new JsonArray().add(1), reply -> {
      if(reply.succeeded()){
        redis.set(new JsonArray().add("first").add("value"), reply2->{
          if(reply2.succeeded()){
            redis.select(new JsonArray().add(0), reply3 ->{
              if(reply3.succeeded()){
                redis.select(new JsonArray().add(1), reply4 -> {
                  if(reply4.succeeded()){
                    redis.get(new JsonArray().add("first"),reply5->{
                      if(reply5.succeeded()){
                        assertTrue("value".equals(reply5.result()));
                        testComplete();
                      }
                    });
                  }
                });
              }
            });
          }
        });
      }
    });
    await();
  }

  @Test
  public void testSet() {
    final String mykey = makeKey();        
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.get(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("Hello", reply1.result());        
            testComplete();
          });
        });
    await();
  }

  @Test
  public void testSetbit() {
    final String mykey = makeKey();
    redis.setbit(toJsonArray(mykey, 7, 1), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(0, reply0.result().longValue());
      redis.setbit(toJsonArray(mykey, 7, 0), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("\u0000", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testSetex() {
    final String mykey = makeKey();
    redis.setex(toJsonArray(mykey, 10, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.ttl(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(10, reply1.result().longValue());
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testSetnx() {
    final String mykey = makeKey();
    redis.setnx(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.setnx(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(0, reply1.result().longValue());
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testSetrange() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello World"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.setrange(toJsonArray(mykey, 6, "Redis"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(11, reply1.result().longValue());
        redis.get(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("Hello Redis", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  @Ignore
  public void testShutdown() throws Exception {

    RedisServer testServer = new RedisServer(6380);
    testServer.start();

    JsonObject job = new JsonObject().put("host", "localhost").put("port", 6380);
    RedisService rdx = RedisService.create(vertx, job);

    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      if (asyncResult.succeeded()) {
        latch.countDown();
      } else {
        throw new RuntimeException("failed to setup", asyncResult.cause());
      }
    });

    awaitLatch(latch);

    rdx.shutdown(new JsonArray().add("NOSAVE"), reply ->{
      fail("server has been terminated. No reply expected");
    });

    rdx.ping(reply ->{
      assertFalse(reply.succeeded());
      testComplete();
    });

  }

  @Test
  public void testSinter() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(1, reply5.result().longValue());
                redis.sinter(toJsonArray(mykey1, mykey2), reply6 -> {
                  assertTrue(reply6.succeeded());
                  assertArrayEquals(new Object[]{"c"}, reply6.result().getList().toArray());
                  testComplete();
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSinterstore() {
    final String mykey = makeKey();
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(1, reply5.result().longValue());
                redis.sinterstore(toJsonArray(mykey, mykey1, mykey2), reply6 -> {
                  assertTrue(reply6.succeeded());
                  assertTrue(reply6.result() == 1);
                  //assertArrayEquals(new Object[]{"c"}, reply6.result().getList().toArray());
                  testComplete();
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSismember() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.sismember(toJsonArray(mykey, "one"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sismember(toJsonArray(mykey, "two"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  @Ignore
  public void testSlaveof() {
  }

  @Test
  @Ignore
  public void testSlowlog() {
  }

  @Test
  public void testSmembers() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "World"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.smembers(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          Object[] expected = new Object[]{"Hello", "World"};
          Object[] result = reply2.result().getList().toArray();
          Arrays.sort(result);
          assertArrayEquals(expected, result);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testSmove() {
    final String mykey = makeKey();
    final String myotherkey = makeKey();
    redis.sadd(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(myotherkey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.smove(toJsonArray(mykey, myotherkey, "two"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.smembers(toJsonArray(mykey), reply4 -> {
              assertTrue(reply4.succeeded());
              Object[] expected = new Object[]{"one"};
              Object[] result = reply4.result().getList().toArray();
              Arrays.sort(result);
              assertArrayEquals(expected, result);
              redis.smembers(toJsonArray(myotherkey), reply5 -> {
                assertTrue(reply5.succeeded());
                Object[] expected1 = new Object[]{"three", "two"};
                Object[] result1 = reply5.result().getList().toArray();
                Arrays.sort(result1);
                assertArrayEquals(expected1, result1);
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSort() {
    final String mykey = makeKey();

    final String k1 = mykey + ":1";
    final String k2 = mykey + ":2";
    final String k3 = mykey + ":3";
    final String kx = mykey + ":*";

    redis.sadd(toJsonArray(mykey, "1", "2", "3"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(3, reply0.result().longValue());
      redis.set(toJsonArray(k1, "one"), reply1 -> {
        assertTrue(reply1.succeeded());
        redis.set(toJsonArray(k2, "two"), reply2 -> {
          assertTrue(reply2.succeeded());
          redis.set(toJsonArray(k3, "three"), reply3 -> {
            assertTrue(reply3.succeeded());
            redis.sort(toJsonArray(mykey, "desc", "get", kx), reply4 -> {
              assertTrue(reply4.succeeded());
              assertArrayEquals(toArray("three", "two", "one"), reply4.result().getList().toArray());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSpop() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.spop(toJsonArray(mykey), reply3 -> {
            assertTrue(reply3.succeeded());
            String ret = reply3.result();
            assertTrue(ret.equals("one") || ret.equals("two") || ret.equals("three"));
            JsonArray expected = new JsonArray();
            if (!ret.equals("one")) {
              expected.add("one");
            }
            if (!ret.equals("two")) {
              expected.add("two");
            }
            if (!ret.equals("three")) {
              expected.add("three");
            }
            redis.smembers(toJsonArray(mykey), reply4 -> {
              assertTrue(reply4.succeeded());
              Object[] expectedA = expected.getList().toArray();
              Arrays.sort(expectedA);
              Object[] res = reply4.result().getList().toArray();
              Arrays.sort(res);
              assertArrayEquals(expectedA, res);
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testSrandmember() {
    //        final String mykey = makeKey();
    //        redis.sadd(toJsonArray(mykey, "one", "two", "three"), reply0 -> {
    //            assertTrue(reply0.succeeded());
    //            assertEquals(3, reply0.result().longValue());
    //            redis.srandmember(toJsonArray(mykey), reply1 -> {
    //                assertTrue(reply1.succeeded());
    //                String randmember = reply1.result().getString(1);
    //                assertTrue(randmember.equals("one") || randmember.equals("two") || randmember.equals("three"));
    //                testComplete();
    //            });
    //        });
    //        await();
  }

  @Test
  public void testSrem() {
    final String mykey = makeKey();
    redis.sadd(toJsonArray(mykey, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(mykey, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.srem(toJsonArray(mykey, "one"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.srem(toJsonArray(mykey, "four"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(0, reply4.result().longValue());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testStrlen() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello world"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.strlen(toJsonArray(mykey), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(11, reply1.result().longValue());
        redis.strlen(toJsonArray("nonexisting"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(0, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test  
  public void testSubscribe() {
    
    String key = makeKey();
    redis.subscribe(toJsonArray(key), reply ->{
      assertTrue(reply.succeeded());
      redis.unsubscribe(toJsonArray(key), reply2 ->{
        assertTrue(reply2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testSunion() {
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
      });
      redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
        assertTrue(reply2.succeeded());
        assertEquals(1, reply2.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
        assertTrue(reply3.succeeded());
        assertEquals(1, reply3.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
        assertTrue(reply4.succeeded());
        assertEquals(1, reply4.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
        assertTrue(reply5.succeeded());
        assertEquals(1, reply5.result().longValue());
        redis.sunion(toJsonArray(mykey1, mykey2), reply6 -> {
          assertTrue(reply6.succeeded());
          JsonArray arr = reply6.result();
          Object[] array = arr.getList().toArray();
          Arrays.sort(array);
          assertTrue(array.length == 5);
          assertArrayEquals(new Object[]{"a", "b", "c", "d", "e"}, array);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testSunionstore() {
    final String mykey = makeKey();
    final String mykey1 = makeKey();
    final String mykey2 = makeKey();

    redis.sadd(toJsonArray(mykey1, "a"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.sadd(toJsonArray(mykey1, "b"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
      });
      redis.sadd(toJsonArray(mykey1, "c"), reply2 -> {
        assertTrue(reply2.succeeded());
        assertEquals(1, reply2.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "c"), reply3 -> {
        assertTrue(reply3.succeeded());
        assertEquals(1, reply3.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "d"), reply4 -> {
        assertTrue(reply4.succeeded());
        assertEquals(1, reply4.result().longValue());
      });
      redis.sadd(toJsonArray(mykey2, "e"), reply5 -> {
        assertTrue(reply5.succeeded());
        assertEquals(1, reply5.result().longValue());
        redis.sunionstore(toJsonArray(mykey,mykey1, mykey2), reply6 -> {
          assertTrue(reply6.succeeded());
          assertTrue(reply6.result() == 5);
          //          JsonArray arr = reply6.result();
          //          Object[] array = arr.getList().toArray();
          //          Arrays.sort(array);
          //          assertTrue(array.length == 5);
          //          assertArrayEquals(new Object[]{"a", "b", "c", "d", "e"}, array);
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  @Ignore
  public void testSync() {
  }

  @Test
  public void testTime() {
    redis.time(reply0 -> {
      assertTrue(reply0.succeeded());
      assertTrue(reply0.result().size() == 2);
      testComplete();
    });
    await();
  }

  @Test
  public void testTtl() {
    final String mykey = makeKey();
    redis.set(toJsonArray(mykey, "Hello"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.expire(toJsonArray(mykey, 10), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.ttl(toJsonArray(mykey), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(10, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testType() {
    final String key1 = makeKey();
    final String key2 = makeKey();
    final String key3 = makeKey();

    redis.set(toJsonArray(key1, "value"), reply0 -> {
      assertTrue(reply0.succeeded());
      redis.lpush(toJsonArray(key2, "value"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.sadd(toJsonArray(key3, "value"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.type(toJsonArray(key1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals("string", reply3.result());
            redis.type(toJsonArray(key2), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals("list", reply4.result());
              redis.type(toJsonArray(key3), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals("set", reply5.result());
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testUnsubscribe() {
    String key = makeKey();
    redis.subscribe(toJsonArray(key), reply ->{
      assertTrue(reply.succeeded());
      redis.unsubscribe(toJsonArray(key), reply2 ->{
        assertTrue(reply2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  @Ignore
  public void testUnwatch() {
  }

  @Test
  @Ignore
  public void testWatch() throws Exception {

    String key = makeKey();
    
    RedisService rdx = RedisService.create(vertx, getConfig());
    CountDownLatch latch = new CountDownLatch(1);
    rdx.start(asyncResult -> {
      assertTrue(asyncResult.succeeded());
      latch.countDown();
    });

    awaitLatch(latch);
    
    CountDownLatch clientLatch = new CountDownLatch(1);
    
    redis.set(toJsonArray(key, 0), reply ->{
      assertTrue(reply.succeeded());
      redis.watch(toJsonArray(key), reply2 ->{
        assertTrue(reply2.succeeded());
        redis.multi(reply3 ->{
          assertTrue(reply3.succeeded());
          redis.incr(toJsonArray(key), reply4 ->{
            assertTrue(reply4.succeeded());
              try {
                clientLatch.wait();                
              }catch(Exception e){}
              redis.incr(toJsonArray(key), reply5 ->{
                assertTrue(reply5.succeeded());
              });  
              redis.incrby(toJsonArray(key, 10), reply6 ->{
                assertTrue(reply6.succeeded());
              });
              redis.exec(reply7 ->{
                /**
                 * Note, this should really fail as we have watched a key and modified
                 * it outside of the multi protocol. However, it does not appear to be 
                 * showing this
                 */
                assertFalse(reply7.succeeded());
                redis.get(toJsonArray(key), reply8 ->{
                  assertTrue(reply8.succeeded());
                  assertTrue(Integer.valueOf(reply8.result()) == 1);
                  testComplete();
                });
              });
            });
          });
        });
      });
    
    rdx.incr(toJsonArray(key), reply ->{
      assertTrue(reply.succeeded());
      clientLatch.countDown();
    });
        
    await();
  }

  @Test
  public void testZadd() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 1, "uno"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 2, "two"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zadd(toJsonArray(key, 3, "two"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(0, reply3.result().longValue());
            redis.zrange(toJsonArray(key, 0, -1, "withscores"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertArrayEquals(toArray("one", "1", "uno", "1", "two", "3"), reply4.result().getList().toArray());
              testComplete();
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZcard() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zcard(toJsonArray(key), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(2, reply2.result().longValue());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testZcount() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zcount(toJsonArray(key, "-inf", "+inf"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(3, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZincrby() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zincrby(toJsonArray(key, 2, "one"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals("3", reply2.result());
          testComplete();
        });
      });
    });
    await();
  }

  @Test
  public void testZinterstore() {
    final String key1 = makeKey();
    final String key2 = makeKey();
    final String key3 = makeKey();

    redis.zadd(toJsonArray(key1, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key1, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key2, 1, "one"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zadd(toJsonArray(key2, 2, "two"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.zadd(toJsonArray(key2, 3, "three"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.zinterstore(toJsonArray(key3, 2, key1, key2, "weights", 2, 3), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(2, reply5.result().longValue());
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrange() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrange(toJsonArray(key, 0, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertArrayEquals(toArray("one", "two", "three"), reply3.result().getList().toArray());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrangebyscore() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrangebyscore(toJsonArray(key, "-inf", "+inf"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertArrayEquals(toArray("one", "two", "three"), reply3.result().getList().toArray());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrank() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrank(toJsonArray(key, "three"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(2, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrem() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrem(toJsonArray(key, "two"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZremrangebyrank() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zremrangebyrank(toJsonArray(key, 0, 1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(2, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZremrangebyscore() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zremrangebyscore(toJsonArray(key, "-inf", "(2"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrevrange() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrevrange(toJsonArray(key, 0, -1), reply3 -> {
            assertTrue(reply3.succeeded());
            assertArrayEquals(toArray("three", "two", "one"), reply3.result().getList().toArray());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrevrangebyscore() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrevrangebyscore(toJsonArray(key, "+inf", "-inf"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertArrayEquals(toArray("three", "two", "one"), reply3.result().getList().toArray());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZrevrank() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key, 3, "three"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zrevrank(toJsonArray(key, "one"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(2, reply3.result().longValue());
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testZscore() {
    final String key = makeKey();
    redis.zadd(toJsonArray(key, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zscore(toJsonArray(key, "one"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals("1", reply1.result());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testZunionstore() {
    final String key1 = makeKey();
    final String key2 = makeKey();
    final String key3 = makeKey();

    redis.zadd(toJsonArray(key1, 1, "one"), reply0 -> {
      assertTrue(reply0.succeeded());
      assertEquals(1, reply0.result().longValue());
      redis.zadd(toJsonArray(key1, 2, "two"), reply1 -> {
        assertTrue(reply1.succeeded());
        assertEquals(1, reply1.result().longValue());
        redis.zadd(toJsonArray(key2, 1, "one"), reply2 -> {
          assertTrue(reply2.succeeded());
          assertEquals(1, reply2.result().longValue());
          redis.zadd(toJsonArray(key2, 2, "two"), reply3 -> {
            assertTrue(reply3.succeeded());
            assertEquals(1, reply3.result().longValue());
            redis.zadd(toJsonArray(key2, 3, "three"), reply4 -> {
              assertTrue(reply4.succeeded());
              assertEquals(1, reply4.result().longValue());
              redis.zunionstore(toJsonArray(key3, 2, key1, key2, "weights", 2, 3), reply5 -> {
                assertTrue(reply5.succeeded());
                assertEquals(3, reply5.result().longValue());
                testComplete();
              });
            });
          });
        });
      });
    });
    await();
  }
}
