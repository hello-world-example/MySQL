# 数据源配置

|           Druid 数据源            |         默认         |             建议              | 说明                                                         |
| :-------------------------------: | :------------------: | :---------------------------: | ------------------------------------------------------------ |
|          **initialSize**          |         `0`          |             `10`              | 初始化时建立物理连接的个数                                   |
|            **minIdle**            |         `0`          |             `10`              | 最小连接池数量（核心链接数）                                 |
|           **maxActive**           |         `8`          |             `50`              | 最大连接池数量                                               |
|            ~~maxIdle~~            |                      |                               | 废弃                                                         |
|            **maxWait**            |         `-1`         |            `1000`             | 当没有可用连接时，等待连接被归还的最大时间，单位毫秒，-1 无限等待 |
|      poolPreparedStatements       |       `false`        |                               | PSCache 对支持游标的数据库性能提升巨大，比如说 Oracle，**在mysql下建议关闭** 。配合 `maxPoolPreparedStatementPerConnectionSize ` **大于0 时，自动触发修改为 true** |
|        **validationQuery**        |        `null`        |          `select 1`           | 检测连接是否有效的 sql，如：`select 'x'`。**如果为null，testOnXxx 都不会起作用** |
|         **testOnBorrow**          |       `false`        |                               | 申请连接时检测连接是否有效，做了这个配置会降低性能           |
|         **testOnReturn**          |       `false`        |                               | 归还连接时检测连接是否有效，做了这个配置会降低性能           |
|         **testWhileIdle**         |        `true`        |                               | 空闲时检查链接是否有效，如果空闲时间大于`timeBetweenEvictionRunsMillis` 进行检查 |
|             keepAlive             |       `false`        |                               | 连接池中的 `minIdle` 数量以内的连接，空闲时间超过`minEvictableIdleTimeMillis`，则会执行`keepAlive`操作。 |
| **timeBetweenEvictionRunsMillis** |  `60000`<br />`1m`   |                               | 1. 检测连接线程执行的间隔时间，如果连接空闲时间大于等于`minEvictableIdleTimeMillis` 则关闭物理连接，-1 不检查<br />2. 获取连接时判断空闲连接是否可用的阀值 |
|  **minEvictableIdleTimeMillis**   | `1800000`<br />`30m` |      `300000`<br />`5m`       | **针对大于核心连接数(minIdle)的链接**，超过空闲时间会被驱逐  |
|  **maxEvictableIdleTimeMillis**   | `25200000`<br />`7h` | 小于 MySQL `wait_timeout`参数 | **针对所有连接**，超过空闲时间会被驱逐，**解决 8 小时问题**，建议检查 `wait_timeout` 参数 |
|       **phyTimeoutMillis**        |         `-1`         |                               | 针对所有连接**，**创建时间**超过该时间被驱逐**               |
|        connectionInitSqls         |                      |                               | 物理连接初始化的时候执行的 sql                               |

## 关键参数评估

### 关注以下现有监控数据

- **对比现有业务 Druid 监控页面**
  - 池中连接数峰值
  - **活跃连接数峰值**
- 集群 TPS
- 实例 TPS
- 业务机器数
- 数据库实例的并发链接峰值
- 数据库并发请求峰值
- SQL 耗时

### 评估参考点

- 一个请求内部会执行多个SQL，一个事务内，使用同一个链接，所以 **maxActive** 应该比数据库并发请求峰值低
  - 参考： **maxActive** >= **服务的 TPS** / 实例个数
  - 参考： **maxActive** >= **数据库并发请求峰值** / 应用个数 / 应用实例个数
- **minIdle** 以现有数据平均值估算
- 为了避免使用的时候新创建链接，**initialSize = minIdle**
- 尽量设置合理的线程大小，避免用到 **maxWait** 的情况
- 并发量过大，大导致链接池不够，假设远程调用超时时间是 2s，**maxWait + SQL耗时** 建议低于 2s
- 链接池不够，**maxWait** 设置过大会导致大量线程卡死，耗尽容器资源，不如快速失败



## Druid、DBCP、C3P0

- Druid
  - 官网：https://github.com/alibaba/druid/
  - 阿里开源，功能全面，支持各种监控、统计功能
- DBCP
  - 官网：http://commons.apache.org/proper/commons-dbcp/
  - 依赖 commons-pool 对象池机制的数据库连接池，Apache 系
- C3P0
  - 官网：https://www.mchange.com/projects/c3p0/
  - 跟随 Hibernate 依赖发布的数据源，历史悠久
- 其他
  - [数据库连接池性能比对(hikari druid c3p0 dbcp jdbc)](https://blog.csdn.net/qq_31125793/article/details/51241943)
    - HikariCP > **Druid** > tomcat-jdbc > **DBCP** > **C3P0** 



## Read More

- [Druid 可配置属性] [DruidDataSource 配置属性列表](https://github.com/alibaba/druid/wiki/DruidDataSource配置属性列表)
- [Druid 配置示例] [DruidDataSource配置](https://github.com/alibaba/druid/wiki/DruidDataSource%E9%85%8D%E7%BD%AE) 
- [常用数据库连接池 (DBCP、c3p0、Druid) 配置说明](https://www.cnblogs.com/JavaSubin/p/5294721.html)