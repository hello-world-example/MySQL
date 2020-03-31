# 连接超时时间

有时系统中会报以下异常

```java
The last packet successfully received from the server was 12,345 milliseconds ago.  
The last packet sent successfully to the server was 67,890 milliseconds ago.; 
nested exception is com.mysql.jdbc.exceptions.jdbc4.CommunicationsException: Communications link failure
```

大致含义是：

- 最后一次从 MySQL Server **接收消息**在 12,345 毫秒前
- 最后一次 **发送消息** 到 MySQL Server 在 67,890 毫秒前
- 异常原因是 **连接失败**，但是造成 连接失败 的原因有很多，常见的有
  - 连接被 MySQL 主动断开（`wait_timeout`）
  - 连接被人工 kill 掉
  - ...

## wait_timeout 和 interactive_timeout

- **wait_timeout**
  - 服务器在关闭 **非交互连接** 之前等待其活动的秒数
  - 默认 `28800s`  即 `8h` 
  - **经典的 8小时问题**，即一个连接空闲 8h，会主动被 MySQL Server 断开
  - 在一些访问量较小 或 定时任务系统中可能会出现，如一天才执行一次的任务，期间连接一直空闲
- **interactive_timeout**
  - 服务器在关闭 **交互式连接** 之前等待其活动的秒数
  - 默认 `28800s`  即 `8h` 

> -  **非交互连接** ：JDBC 连接，`wait_timeout` 继承全局 `wait_timeout`
> -  **交互式连接** ：MySQL 控制台连接，`wait_timeout` 继承全局 `interactive_timeout`
> - 总结：~~无需关注连接方式~~，**关注 `wait_timeout`  即可，其值会覆盖  `interactive_timeout`**

### 测试

```mysql
-- 默认 8h
mysql> show variables like 'wait_timeout';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| wait_timeout  | 28800 |
+---------------+-------+

-- 修改为 5s
mysql> set session wait_timeout = 5;
-- 查看修改结果
mysql> show variables like 'wait_timeout';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| wait_timeout  | 5     |
+---------------+-------+

mysql> -- 等待 5s 以上
mysql> show variables like 'wait_timeout';
ERROR 2006 (HY000): MySQL server has gone away 
No connection. Trying to reconnect...
Connection id:    4
Current database: *** NONE ***
```

## 异常场景测试

### 测试程序

```java
Class.forName("com.mysql.jdbc.Driver");
@Cleanup Connection connection = DriverManager.getConnection(
  "jdbc:mysql://localhost:3307/mysql?autoReconnect=false", "root", "123456"
);

@Cleanup Statement statement = connection.createStatement();
for (int i = 0; i < 100; i++) {
  try {
    @Cleanup ResultSet resultSet = statement.executeQuery("show variables like 'wait_timeout'");
    resultSet.next();
    System.out.println(resultSet.getInt(2));
  } catch (Exception ex) {
    // 出现异常继续
    System.err.println(ex.getMessage());
  }
  // 每次查询，中间等待 5s
  TimeUnit.SECONDS.sleep(5);
}
```



### 场景： 主动 kill 连接

运行测试程序过程中，查看服务端进程

```mysql
-- 因为 show variables like 'wait_timeout' 语句速度很快，大部分时间都处于 Sleep 状态
-- 每次查询 中间等待 5s，Time 列可能会出现 0~5 之间的数字，每次查询会重新 归 0
-- 因为程序一直使用的同一个连接 Id 列 11 不会变
mysql> show processlist;
+----+------+------------------+-------+---------+------+----------+------------------+
| Id | User | Host             | db    | Command | Time | State    | Info             |
+----+------+------------------+-------+---------+------+----------+------------------+
|  4 | root | localhost        | NULL  | Query   |    0 | starting | show processlist |
| 11 | root | 172.17.0.1:35364 | mysql | Sleep   |    4 |          | NULL             |
+----+------+------------------+-------+---------+------+----------+------------------+
```

kill 掉查看效果

```mysql 
mysql> kill 11;

-- 程序报错
Communications link failure; 
The last packet successfully received from the server was 5,031 milliseconds ago.  
The last packet sent successfully to the server was 26 milliseconds ago.
...
-- 由于 autoReconnect=false， 后续会报以下错误
No operations allowed after statement closed.
No operations allowed after statement closed.
...
```



### 场景： 等待 wait_timeout 超时

- **因为修改系统参数，对已创建的连接无效**
- 所以需要先进行以下设置，再运行程序

```mysql
mysql> set global wait_timeout = 3;

mysql> show global variables like 'wait_timeout';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| wait_timeout  | 3     |
+---------------+-------+
```

查看运行效果

```mysql 
-- 第一次连接成功后，立马查询
3

-- 第二次，因为中间等待了 5 秒，报以下异常
Communications link failure
The last packet successfully received from the server was 5,026 milliseconds ago.  
The last packet sent successfully to the server was 23 milliseconds ago.

-- 之后全都报以下异常
No operations allowed after statement closed.
No operations allowed after statement closed.
```

### 场景： autoReconnect

- 把 `autoReconnect` 设置为 `true`，即  `"jdbc:mysql://localhost:3307/mysql?autoReconnect=true"`
- 测试自动重连参数的效果

运行测试程序过程中，查看服务端进程

```mysql
-- 查看进程
mysql> show processlist;
+----+------+------------------+-------+---------+------+----------+------------------+
| Id | User | Host             | db    | Command | Time | State    | Info             |
+----+------+------------------+-------+---------+------+----------+------------------+
|  4 | root | localhost        | NULL  | Query   |    0 | starting | show processlist |
| 16 | root | 172.17.0.1:35384 | mysql | Sleep   |    5 |          | NULL             |
+----+------+------------------+-------+---------+------+----------+------------------+
-- 杀掉 
mysql> kill 16;

-- ============================== 程序控制台

-- 开始正常
..
28800
-- kill 掉之后，报
Communications link failure
The last packet successfully received from the server was 5,023 milliseconds ago.  
The last packet sent successfully to the server was 19 milliseconds ago.

-- 下次重新查询，正常，没有报 No operations allowed after statement closed.
-- statement 对象没有被关闭，进行了重连
28800
...
```



### 小结

- `Communications link failure` 失败由连接断开导致，但是连接断开的原因各种各样
- `autoReconnect=true` **无法避免在使用过程中异常的产生，只能下次自动重连，并非内部自动重连**
- `autoReconnect` 开启后一般配合 `failOverReadOnly`，默认是 `true`，需要设置为 `false`
- `autoReconnect` 默认是 `false`，官方 **并不建议把其设置为 `true`**，原因：
  - 当程序无法正确处理 `SQLException` 时，可能会产生数据一致性的问题，如：一个事务执行了一半连接断开，假如程序的逻辑是继续往下执行，则后续操作会开启一个新的连接
  - `autoReconnect` 该参数被设计用在 程序无法正确处理死连接的场景
  - 官方建议 调大 `wait_timeout` （大于 8h）
  - 详见： Properties for Connector/J > High Availability and Clustering > [autoReconnect](https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-configuration-properties.html)
- 官方建议貌似也不太靠谱
  - 调大 `wait_timeout` 会导致大量的空闲连接占用服务器资源
  - `autoReconnect` 开启担心的场景，实际业务并不常见，但是也算有一定风险
- 对于使用连接池的应用，**建议通过连接池的检查机制解决**



## 连接池检查异常连接

以目前 最新版的 `com.alibaba:druid:1.1.21` 举例，通过以下参数进行检查。

> 全部参数详见 ： [DataSource 配置](/MySQL/docs/Jdbc/DataSource/)



### Druid 定时扫描销毁连接

```java
public class DruidDataSource ... {

  // 启动销毁线程
  protected void createAndStartDestroyThread() {
    // 销毁 Connection 的任务
    destroyTask = new DestroyTask();

    if (destroyScheduler != null) {
      long period = timeBetweenEvictionRunsMillis;
      if (period <= 0) {
        period = 1000;
      }

      // 每 timeBetweenEvictionRunsMillis 执行一次
      destroySchedulerFuture = destroyScheduler.scheduleAtFixedRate(destroyTask, period, period, TimeUnit.MILLISECONDS);

      initedLatch.countDown();
      return;
    }

    String threadName = "Druid-ConnectionPool-Destroy-" + System.identityHashCode(this);
    // DestroyConnectionThread 循环调用 DestroyTask.run
    // 每次 Thread.sleep(timeBetweenEvictionRunsMillis);
    destroyConnectionThread = new DestroyConnectionThread(threadName);
    destroyConnectionThread.start();
  }

  // 销毁任务
  public class DestroyTask implements Runnable {
    public void run() {
      // 计算需要释放 Connection
      // keepAlive 默认是 false
      shrink(true, keepAlive);
      // 
      if (isRemoveAbandoned()) {
        removeAbandoned();
      }
    }
  }

  public void shrink(boolean checkTime, boolean keepAlive) {

    // ...

    // ❤❤❤ checkCount 需要检查的个数 = 池子大小 - minIdle 最小连接池数量（核心连接数）
    final int checkCount = poolingCount - minIdle;
    final long currentTimeMillis = System.currentTimeMillis();

    for (int i = 0; i < poolingCount; ++i) {
      DruidConnectionHolder connection = connections[i];

      if ((onFatalError || fatalErrorIncrement > 0) && (lastFatalErrorTimeMillis > connection.connectTimeMillis))  {
        keepAliveConnections[keepAliveCount++] = connection;
        continue;
      }

      if (checkTime) {
        // ❤❤❤❤ phyTimeoutMillis 默认 -1
        if (phyTimeoutMillis > 0) {
          // 无连接真实的创建时间 = 当前时间 - 连接时间
          long phyConnectTimeMillis = currentTimeMillis - connection.connectTimeMillis;
          // 创建时间过程 Connection 被淘汰
          if (phyConnectTimeMillis > phyTimeoutMillis) {
            evictConnections[evictCount++] = connection;
            continue;
          }
        }

        // 空闲时间 = 当前时间 - 最后一次连接被使用的时间
        long idleMillis = currentTimeMillis - connection.lastActiveTimeMillis;

        // 空闲时间 < minEvictableIdleTimeMillis (最小淘汰时间)，默认 30m
        // 空闲时间 < keepAliveBetweenTimeMillis 默认 2m
        if (idleMillis < minEvictableIdleTimeMillis && idleMillis < keepAliveBetweenTimeMillis ) {
          // 因为每次申请连接都是从 Pool(队列) 最后获取，
          // 所以 越靠头部 越空闲，遍历是从头部遍历的，越往后越活跃，越不空闲
          // 所以 当前连接的空闲时间如果小于驱逐时间，可以判断后续连接都小于，可以退出遍历
          break;
        }

        // ❤❤❤ 空闲时间 >= minEvictableIdleTimeMillis (最小淘汰时间)，默认 30m
        if (idleMillis >= minEvictableIdleTimeMillis) {
          // checkCount 需要检查的个数 = 池子大小 - minIdle 最小连接池数量
          if (checkTime && i < checkCount) {
            // ❤❤❤ 淘汰连接是从头 开始淘汰，因为约靠头的越空闲
            // ❤❤❤ 但是因为 checkCount 的控制，会保留 minIdle 个连接
            // ❤❤❤ 如果保留的 minIdle 个连接超过了 minEvictableIdleTimeMillis 则管不到
            evictConnections[evictCount++] = connection;
            continue;
          } else if (idleMillis > maxEvictableIdleTimeMillis) {
            // ❤❤❤ 如果 空闲时间 > maxEvictableIdleTimeMillis（7h），也会也会被淘汰，
            // ❤❤❤ 避免 minIdle（最小核心连接数）设置的太多，头部的连接一直用不到，
            // ❤❤❤ 导致空闲超过 wait_timeout，会被 MySQL Server 主动断开
            evictConnections[evictCount++] = connection;
            continue;
          }
        } 

        // keepAlive 默认是 false
        if (keepAlive && idleMillis >= keepAliveBetweenTimeMillis) {
          keepAliveConnections[keepAliveCount++] = connection;
        }
      } else {
        // ❤❤❤ 淘汰所有头部比较闲的连接， 保留 minIdle 个连接
        if (i < checkCount) {
          evictConnections[evictCount++] = connection;
        } else {
          break;
        }
      } // if (checkTime) end
    }// for end

    // 整理池子

    // 释放连接
    if (evictCount > 0) {
      for (int i = 0; i < evictCount; ++i) {
        DruidConnectionHolder item = evictConnections[i];
        Connection connection = item.getConnection();
        JdbcUtils.close(connection);
        destroyCountUpdater.incrementAndGet(this);
      }
      Arrays.fill(evictConnections, null);
    }

    // ...
    
  }
}
```

### Druid 获取连接

```java
public class DruidDataSource ... {

  @Override
  public DruidPooledConnection getConnection() throws SQLException {
    // maxWait 默认 -1 无限等待
    return getConnection(maxWait);
  }

  public DruidPooledConnection getConnection(long maxWaitMillis) throws SQLException {
    init();

    if (filters.size() > 0) {
      FilterChainImpl filterChain = new FilterChainImpl(this);
      return filterChain.dataSource_connect(this, maxWaitMillis);
    } else {
      // 获取连接
      return getConnectionDirect(maxWaitMillis);
    }
  }

  public DruidPooledConnection getConnectionDirect(long maxWaitMillis) throws SQLException {
    int notFullTimeoutRetryCnt = 0;
    for (;;) {
      DruidPooledConnection poolableConnection;
      try {
        // 从队尾获取连接
        poolableConnection = getConnectionInternal(maxWaitMillis);
      } catch (GetConnectionTimeoutException ex) {
        ...
        throw ex;
      }

      // ❤❤ testOnBorrow 
      if (testOnBorrow) {
        // 校验连接 执行 validationQuery
        boolean validate = testConnectionInternal(poolableConnection.holder, poolableConnection.conn);
        // 如果不可用
        if (!validate) {
          // ..
          // 释放 连接
          discardConnection(poolableConnection.holder);
          // 重新获取
          continue;
        }
      } else {
        // testOnBorrow 为 false， 检查 真实的 Connection 是否被标记为 Close
        if (poolableConnection.conn.isClosed()) {
          // 关闭的连接释放 
          discardConnection(poolableConnection.holder); 
          // 重新获取
          continue;
        }
        // ❤❤ testWhileIdle 
        if (testWhileIdle) {
          // ...
          // 空闲时间
          long idleMillis = currentTimeMillis - lastActiveTimeMillis;
          // 检查周期
          long timeBetweenEvictionRunsMillis = this.timeBetweenEvictionRunsMillis;
          // 如果没有设置，强制设置默认值 60s
          if (timeBetweenEvictionRunsMillis <= 0) {
            timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
          }
          // ❤❤❤ 空闲时间 >= 扫描周期（未设置的时候是 60s ），主动触发校验
          // ❤❤❤ 相当于获取连接的时候，默认如果连接空闲超过 1m 就会被检查
          // ❤❤❤ 跟想象中不一样， timeBetweenEvictionRunsMillis 有两个含义 ❤❤❤
          // ❤❤❤ 1. 后台任务检查空闲连接的周期 2. 获取连接时判断空闲连接是否可用的阀值 ❤❤❤ 
          if (idleMillis >= timeBetweenEvictionRunsMillis || idleMillis < 0) {
            // 校验连接 执行 validationQuery
            boolean validate = testConnectionInternal(poolableConnection.holder, poolableConnection.conn);
            if (!validate) {
              // ..
              // 释放 连接
              discardConnection(poolableConnection.holder);
              // 重新获取
              continue;
            }
          }
        }
      }

      // ...

      if (!this.defaultAutoCommit) {
        poolableConnection.setAutoCommit(false);
      }

      return poolableConnection;
      
    } // for (;;)
  }

}
```



### Druid 连接池小结

- 只配置 `validationQuery`，其他参数全都默认情况下
- 获取连接
  - 检查连接是否 `close`
  - 如果连接空闲超过 `timeBetweenEvictionRunsMillis(1m)` 检查可用性
- 后台淘汰任务
  - 每  `timeBetweenEvictionRunsMillis(1m)`  执行一次
  - 非核心线程 `minIdle(0)` 检查 空闲时间大于 `minEvictableIdleTimeMillis(30m)` 淘汰
  - 所有连接，检查 空闲时间大于 `maxEvictableIdleTimeMillis(7h)` 淘汰
- **总结：使用 Druid，不可能出现 8 小时问题**
  - **只要配了  `validationQuery`**，获取连接时，空闲超过 `1m` 就会被检查，**单这一步就能避免**
  - **如果都没配**，后台定时任务，`maxEvictableIdleTimeMillis(7h)` 会针对所有连接检查空闲连接
- 连接被强制 `kill` 的情况
  - Druid 的连接检查机制，无法处理 **使用过程中** 连接被强制 `kill` 的
  - `autoReconnect` 也不行
  - 两个都可以保证下次使用的时候，连接是可以用的
  - **所以 `autoReconnect` 可配可不配，建议不用配置，使用 Druid 的连接检查机制即可**



## 确定是否 wait_timeout 问题

- 检查 MySQL `wait_timeout` 设置的多少
- 查看 `The last packet successfully received from the server was xxx,xxx milliseconds ago` 中报的时间是多少
- 如果 `xxx,xxx milliseconds` 大于 `wait_timeout` 设置的时间，则很要可能是被 MySQL Server 主动关闭
- 如果是 `xxx,xxx milliseconds` 小于  `wait_timeout`，则考虑是否是被人工 `kill`
- 如果是 `xxx,xxx milliseconds` 徘徊在某个时间区间以内，考虑是否有定时任务在 `kill` 慢SQL
  - 如： [pt-kill](https://www.cnblogs.com/wjoyxt/p/6025846.html) 杀死指定用户超过 xx秒的查询，以此避免服务被 慢 SQL 拖垮



## Read More

- Server System Variables - [wait_timeout](https://dev.mysql.com/doc/refman/5.7/en/server-system-variables.html#sysvar_wait_timeout)
- [MySQL 中 interactive_timeout 和 wait_timeout 的区别](https://www.cnblogs.com/ivictor/p/5979731.html)
- Configuration Properties for Connector/J > High Availability and Clustering > [autoReconnect](https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-configuration-properties.html)
- [Percona Toolkit](https://github.com/percona/percona-toolkit) 工具包（`pt-kill` ： kill 掉符合指定条件mysql语句）
- [linux 下 percona-toolkit 工具包的安装和使用（超详细版）](https://www.cnblogs.com/zishengY/p/6852280.html)


