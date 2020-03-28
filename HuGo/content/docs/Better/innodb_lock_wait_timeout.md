# 锁等待超时



### 锁超时(Lock wait timeout) 和 死锁(Dead Lock) 

- **Lock wait timeout**
  - 后面的事务等待前面处理的事务释放锁，等待时间超过了`innodb_lock_wait_timeout`
  - 在超时时间内，后来的事务会一直等待前面的事务释放锁，会导致程序阻塞
  - **当发生锁等待超时时，将回滚当前语句（而不是整个事务）** 
  -  `ERROR 1205 (HY000): Lock wait timeout exceeded; try restarting transaction` 异常
  - **出现的原因：** 事务过大，锁占用的时间过长
- **Dead Lock** 死锁
  - 两个事务互相等待对方释放相同资源的锁
  - 死锁产生后会立即失败，随机回滚其中一个事务（后操作造成死锁的事务）
  - 当 `innodb_deadlock_detect` 被禁用时，死锁的超时时间即 `innodb_lock_wait_timeout`
  - `ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction`
  - **出现的原因：** 多个事务对同一批数据进行操作时，加锁顺序不一致



### innodb_lock_wait_timeout 和 lock_wait_timeout

> - [Server System Variable Reference](https://dev.mysql.com/doc/refman/5.7/en/server-system-variable-reference.html)
>   - [innodb_lock_wait_timeout](https://dev.mysql.com/doc/refman/5.7/en/innodb-parameters.html#sysvar_innodb_lock_wait_timeout)
>   - [lock_wait_timeout](https://dev.mysql.com/doc/refman/5.7/en/server-system-variables.html#sysvar_lock_wait_timeout)



- `innodb_lock_wait_timeout`
  - 针对行级锁，不适用与表锁
  - 单位秒，默认 `50`，可选值 1 ~ 1073741824
  - 对于高度交互的应用程序 或 **OLTP系统，可能会降低此值，以便快速响应用户反馈**
  - 对于等待其他大型插入或更新操作完成的数据仓库，可以增加此值
- `lock_wait_timeout`
  - DDL 和 DML 对 表、视图、存储过程、函数 的操作
  - 常见如表锁等
  - 单位秒，默认 31536000 (1年)，可选值 1 ~ 31536000



### 修改 innodb_lock_wait_timeout

```mysql
# 锁超时时间设置为 5s
mysql> set session innodb_lock_wait_timeout = 5;

mysql> show variables like '%lock_wait_timeout%';
+--------------------------+----------+
| Variable_name            | Value    |
+--------------------------+----------+
| innodb_lock_wait_timeout | 5       |
| lock_wait_timeout        | 31536000 |
+--------------------------+----------+
```



## 测试场景

### 数据准备

```mysql
-- 创建测试表
CREATE TABLE `T_TEST` (
  `ID` bigint(11) NOT NULL AUTO_INCREMENT,
  `UNIQ` varchar(20) NOT NULL,
  `NAME` varchar(20) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UNIQ_FILED_UNIQ` (`UNIQ`) USING BTREE
) ENGINE=InnoDB;

-- 插入测试数据
insert into `T_TEST` (`ID`, `UNIQ`, `NAME`) values ( 1, 'Kail1', 'Kail1');
insert into `T_TEST` (`ID`, `UNIQ`, `NAME`) values ( 2, 'Kail2', 'Kail2');
```



### 修改隔离级别

MySQL 默认的隔离级别是 **可重复读**（`REPEATABLE-READ`），为了和公司设置保持一致，这里改为 **读提交**（`READ-COMMITTED`）

```mysql
-- select @@global.tx_isolation,@@tx_isolation; 也可以
mysql> select @@global.transaction_isolation,@@transaction_isolation;
+--------------------------------+-------------------------+
| @@global.transaction_isolation | @@transaction_isolation |
+--------------------------------+-------------------------+
| REPEATABLE-READ                | REPEATABLE-READ         |
+--------------------------------+-------------------------+

mysql> show session variables like '%tx%';
+---------------+-----------------+
| Variable_name | Value           |
+---------------+-----------------+
| tx_isolation  | REPEATABLE-READ |
| tx_read_only  | OFF             |
+---------------+-----------------+

-- 设置 Session 级别的隔离级别，用于测试（设置的时候 READ COMMITTED 中间没有横岗）
-- 全局级别 SET GLOBAL TRANSACTION ISOLATION LEVEL xxx;
mysql> SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```



### 事务提交方式

```mysql
mysql> set session autocommit=0;
  
-- 关闭自动提交
mysql> show session variables like 'autocommit';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| autocommit    | OFF   |
+---------------+-------+
```



### 测试前提

- 需要分别开启两个会话，创造多事务环境
- 关闭事务自动提交，通过 `commit` 自动提交



### 场景：死锁 立即失败

- `ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction`
- 表中分别有两行数据
- 事务1 先更新 1 后更新 2，事务2 先更新 2 后更新 1，以此来创造死锁

```mysql
-- 😀 事务1： 操作第一条数据， 对第一条数据加锁
mysql> UPDATE T_TEST SET NAME='tx111' WHERE ID = 1;
Query OK, 1 row affected (0.00 sec)
-- ☠️ 事务2： 操作第二条数据， 对第二条数据加锁
mysql> UPDATE T_TEST SET NAME='tx222' WHERE ID = 2;
Query OK, 1 row affected (0.00 sec)


-- 😀 事务1：第2条数据已经被 事务2 占用，这里不会立即返回，❤❤ 阻塞等待 ❤❤
mysql> UPDATE T_TEST SET NAME='tx111' WHERE ID = 2;
-- ☠️☠️☠️☠️ 事务2：第1条数据已经被 事务1 占用，这里检查到死锁，❤❤ 立即报错 ❤❤ ☠️☠️☠️☠️
mysql> UPDATE T_TEST SET NAME='tx222' WHERE ID = 1;
ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
-- 😀 事务1（第一个会话窗口）：解除阻塞，输出影响的行数
Query OK, 1 row affected (14.25 sec)


-- 😀 会话窗口1
mysql> commit;
-- ☠️ 会话窗口2
mysql> commit;


-- 查询结果，因为事务二被回滚，结果为事务1 的更新结果
mysql> SELECT * FROM T_TEST;
+----+-------+-------+
| ID | UNIQ  | NAME  |
+----+-------+-------+
|  1 | Kail1 | tx111 |
|  2 | Kail2 | tx111 |
+----+-------+-------+
```



### 场景：锁超时 阻塞等待

```mysql
-- 先把超时时间设置为 5 秒
mysql> set session innodb_lock_wait_timeout = 5;

-- 😀 事务1： 操作第一条数据， 对第一条数据加锁
mysql> UPDATE T_TEST SET NAME='tx-111' WHERE ID = 1;
Query OK, 1 row affected (0.00 sec)
-- ☠️ 事务2： 也操作第一条数据，❤❤❤ 因为 事务1 还没提交，这里会等待，直到超时 ❤❤❤
mysql> UPDATE T_TEST SET NAME='tx-222' WHERE ID = 1;
-- 5 秒后会报下面这个错
ERROR 1205 (HY000): Lock wait timeout exceeded; try restarting transaction
```



### ❤ 场景：锁超时 仅回滚当前语句

```mysql
-- 先把超时时间设置为 5 秒
mysql> set session innodb_lock_wait_timeout = 5;

-- 😀 事务1： 操作第一条数据， 对第一条数据加锁
mysql> UPDATE T_TEST SET NAME='tx-1' WHERE ID = 1;
Query OK, 1 row affected (0.00 sec)
-- ☠️ 事务2： 先更新第二条数据，立即返回
mysql> UPDATE T_TEST SET NAME='tx-2' WHERE ID = 2;
Query OK, 1 row affected (0.00 sec)
-- ☠️ 事务2： 再更新第一条数据，❤❤❤ 阻塞直到超时 ❤❤❤
mysql> UPDATE T_TEST SET NAME='tx-2' WHERE ID = 1;
ERROR 1205 (HY000): Lock wait timeout exceeded; try restarting transaction

-- 😀 事务1： 能看到自己刚刚更新的结果，看不到事务 2
mysql> SELECT * FROM T_TEST;
+----+-------+-------+
| ID | UNIQ  | NAME  |
+----+-------+-------+
|  1 | Kail1 | tx-1  |
|  2 | Kail2 | tx111 |
+----+-------+-------+
-- ☠️ 事务2： 能看到自己刚刚更新的结果，看不到事务 1
mysql> SELECT * FROM T_TEST;
+----+-------+-------+
| ID | UNIQ  | NAME  |
+----+-------+-------+
|  1 | Kail1 | tx111 |
|  2 | Kail2 | tx-2  |
+----+-------+-------+

-- 😀 事务1： 提交事务
mysql> commit;
-- ☠️ 事务2： 提交事务
mysql> commit;

-- 最终效果，除了 事务2 锁超时执行失败的语句，其他两条语句实际上都执行成功
mysql> SELECT * FROM T_TEST;
+----+-------+------+
| ID | UNIQ  | NAME |
+----+-------+------+
|  1 | Kail1 | tx-1 |
|  2 | Kail2 | tx-2 |
+----+-------+------+
```

#### 小结 - 仅回滚当前语句

- 正如官方文档所说： **当发生锁等待超时时，将回滚当前语句（而不是整个事务）** 
- 实际业务系统中**如果出现锁超时**，**一个事务中只回滚一部分，会导致数据不一致吗**？
- 答案是 **不会**
  - 因为事务正确的使用方式并不是上面在 Console 中的操作方式
  - 实际操作方式是： 如果 SQL 执行成功 `commit`，如果执行不成功 `rollback`，整个事务就会回滚
  - 而上面的操作，不管是否成功，全都 `commit` 了，这种情况在实际业务编码中并不常见
- 侧面也说明，假如业务中允许在锁超时的时候部分提交，其实是可以实现的
- 当前实验场景中的 **“回滚当前语句”** 如果改成 **“当前语句执行失败”** 可能会更好，减少理解上的误区



## 问题排查

### 查看当前等待的事务

```mysql
mysql> SELECT * FROM information_schema.INNODB_TRX WHERE trx_state='LOCK WAIT' \G
*************************** 1. row ***************************
                    trx_id: 21096
                 trx_state: LOCK WAIT
               trx_started: 2020-03-27 11:04:54
     trx_requested_lock_id: 21096:51:3:11
          trx_wait_started: 2020-03-27 11:05:49
                trx_weight: 2
       trx_mysql_thread_id: 25
                 trx_query: UPDATE T_TEST SET NAME='tx-222' WHERE ID = 1
       trx_operation_state: starting index read
         trx_tables_in_use: 1
         trx_tables_locked: 1
          trx_lock_structs: 2
     trx_lock_memory_bytes: 1136
           trx_rows_locked: 2
         trx_rows_modified: 0
   trx_concurrency_tickets: 0
       trx_isolation_level: READ COMMITTED
         trx_unique_checks: 1
    trx_foreign_key_checks: 1
trx_last_foreign_key_error: NULL
 trx_adaptive_hash_latched: 0
 trx_adaptive_hash_timeout: 0
          trx_is_read_only: 0
trx_autocommit_non_locking: 0
```

### LOCK WAIT 的事务在等谁

```mysql
mysql> SELECT * FROM information_schema.INNODB_LOCK_WAITS;
+-------------------+-------------------+-----------------+------------------+
| requesting_trx_id | requested_lock_id | blocking_trx_id | blocking_lock_id |
+-------------------+-------------------+-----------------+------------------+
| 21096             | 21096:51:3:11     | 21095           | 21095:51:3:11    |
+-------------------+-------------------+-----------------+------------------+
1 row in set, 1 warning (0.01 sec)

-- 综合 information_schema.INNODB_LOCK_WAITS 和 information_schema.INNODB_TRX
mysql> SELECT
	r.trx_id waiting_trx_id ,
	r.trx_mysql_thread_id waiting_thread ,
	r.trx_query waiting_query ,
	b.trx_id blocking_trx_id ,
	b.trx_mysql_thread_id blocking_thread ,
	b.trx_query blocking_query
FROM
	information_schema.INNODB_LOCK_WAITS w
INNER JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.INNODB_TRX r ON r.trx_id = w.requesting_trx_id \G
*************************** 1. row ***************************
 waiting_trx_id: 21104
 waiting_thread: 25
  waiting_query: UPDATE T_TEST SET NAME='tx-222' WHERE ID = 1
blocking_trx_id: 21102
blocking_thread: 24
 blocking_query: NULL
1 row in set, 1 warning (0.00 sec)
```



### 找到阻塞的源头

> - 可以找到 阻塞源头 是哪个事务
>
> - 无法找到 阻塞源头 是哪条 SQL
>
> - **可以找到 阻塞源头 的事务执行的 最后一条SQL**
>
>   - [MySQL Innodb如何找出阻塞事务源头SQL](https://www.cnblogs.com/kerrycode/p/8948335.html)
>
>   - [为什么数据库有时候不能定位阻塞（Blocker）源头的SQL语句](https://www.cnblogs.com/kerrycode/p/5821413.html)

```mysql
SELECT
     state.SQL_TEXT, -- 阻塞源头 的事务执行的 最后一条SQL
     state.DIGEST_TEXT,
     thread.PROCESSLIST_ID,
     thread.PROCESSLIST_USER, -- 来源用户
     thread.PROCESSLIST_HOST, -- 来源主机
     thread.PROCESSLIST_DB,
     tx.trx_id,
     tx.trx_state,
     tx.trx_started           -- 开始时间
FROM
     performance_schema.events_statements_current state
INNER JOIN performance_schema.threads thread ON state.thread_id = thread.thread_id
INNER JOIN information_schema.INNODB_TRX tx ON thread.processlist_id = tx.trx_mysql_thread_id
WHERE
-- 根据事务 ID 查询
	tx.trx_id = '21102'
-- 根据 show processlist 中的 ID 查询
-- tx.trx_mysql_thread_id = 'processlist ID'
ORDER BY
	tx.trx_started \G
```

### general_log 开启记录

- `general_log` 将所有到达 MySQL Server 的 SQL 语句记录下来
- 但是一般不会开启开功能，因为log的量会非常庞大
- 个别情况下可能会临时的开一会儿以供排障使用
- 相关参数有
  - `general_log` 日志是否开启
  - `log_output` 日志输出类型
    - `FILE` 日志写到 `general_log_file` 指定的文件中
    - `TABLE` 日志写到 `mysql.slow_log` 表中

```mysql
mysql> show variables like '%log_output%';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| log_output    | FILE  |
+---------------+-------+

mysql> show variables like '%general_log%';
+------------------+---------------------------------+
| Variable_name    | Value                           |
+------------------+---------------------------------+
| general_log      | OFF                             |
| general_log_file | /var/lib/mysql/35cb5b325ec1.log |
+------------------+---------------------------------+

-- 开启 general_log
mysql> set GLOBAL general_log = 'ON';

-- 通过 INNODB_TRX 和 INNODB_LOCK_WAITS 表，找到阻塞的 trx_mysql_thread_id
-- 过滤出事务中指定的 SQL
$ tail -fn 400 /var/lib/mysql/35cb5b325ec1.log | grep "{trx_mysql_thread_id}"
Time                              Id Command    Argument
..
2020-03-28T07:08:11.002374Z        33 Connect   root@localhost on test using Socket
2020-03-28T07:08:11.005286Z        33 Query     show databases
2020-03-28T07:08:11.011642Z        33 Query     show tables
2020-03-28T07:08:11.016498Z        33 Field List        T_TEST 
2020-03-28T07:08:11.019267Z        33 Field List        T_USER 
2020-03-28T07:08:11.028182Z        33 Query     UPDATE T_TEST SET NAME='tx-112' WHERE ID = 1
```

### ❤ 更快速的方法

> - `sys` 库所有的数据源来自 `performance_schema`
> - 目标是把 `performance_schema` 的把复杂度降低
> -  `sys` 库 基本上由 **视图** 和 **存储过程** 组成

```mysql
mysql> select * from sys.innodb_lock_waits \G
*************************** 1. row ***************************
                wait_started: 2020-03-28 07:27:56
                    wait_age: 00:00:24
               wait_age_secs: 24
                locked_table: `test`.`T_TEST`
                locked_index: PRIMARY
                 locked_type: RECORD
              waiting_trx_id: 21115
         waiting_trx_started: 2020-03-28 07:09:09
             waiting_trx_age: 00:19:11
     waiting_trx_rows_locked: 3
   waiting_trx_rows_modified: 0
                 waiting_pid: 34
               waiting_query: UPDATE T_TEST SET NAME='tx-222' WHERE ID = 1
             waiting_lock_id: 21115:51:3:11
           waiting_lock_mode: X
             blocking_trx_id: 21114
                blocking_pid: 33
              blocking_query: NULL
            blocking_lock_id: 21114:51:3:11
          blocking_lock_mode: X
        blocking_trx_started: 2020-03-28 07:09:05
            blocking_trx_age: 00:19:15
    blocking_trx_rows_locked: 1
  blocking_trx_rows_modified: 0
     sql_kill_blocking_query: KILL QUERY 33
sql_kill_blocking_connection: KILL 33

mysql> SELECT * FROM sys.processlist WHERE conn_id = 33 \G
*************************** 1. row ***************************
                thd_id: 58
               conn_id: 33
                  user: root@localhost
                    db: test
               command: Sleep
                 state: NULL
                  time: 1665
     current_statement: NULL
     statement_latency: NULL
              progress: NULL
          lock_latency: 4.53 ms
         rows_examined: 1
             rows_sent: 0
         rows_affected: 0
            tmp_tables: 0
       tmp_disk_tables: 0
             full_scan: NO
        last_statement: UPDATE T_TEST SET NAME='tx-111' WHERE ID = 1 -- 阻塞来源最后执行的SQL
last_statement_latency: 11.39 ms
        current_memory: 0 bytes
             last_wait: NULL
     last_wait_latency: NULL
                source: NULL
           trx_latency: NULL
             trx_state: NULL
        trx_autocommit: NULL
                   pid: 90
          program_name: mysql
```



### ❤ 锁等待时间统计 

> - [Server Status Variable Reference](https://dev.mysql.com/doc/refman/5.7/en/server-status-variable-reference.html)
>   - [Innodb_row_lock_time_avg](https://dev.mysql.com/doc/refman/5.7/en/server-status-variables.html#statvar_Innodb_row_lock_time_avg)

```mysql
mysql> SELECT * FROM sys.metrics WHERE Variable_name LIKE 'innodb_row_lock_%';
+-------------------------------+----------------+---------------+---------+
| Variable_name                 | Variable_value | Type          | Enabled |
+-------------------------------+----------------+---------------+---------+
| innodb_row_lock_current_waits | 0              | Global Status | YES     |
| innodb_row_lock_time          | 1508173        | Global Status | YES     |
| innodb_row_lock_time_avg      | 41893          | Global Status | YES     |
| innodb_row_lock_time_max      | 51918          | Global Status | YES     |
| innodb_row_lock_waits         | 36             | Global Status | YES     |
+-------------------------------+----------------+---------------+---------+

-- 或者
mysql> show status like 'innodb_row_lock%';
+-------------------------------+---------+
| Variable_name                 | Value   |
+-------------------------------+---------+
| Innodb_row_lock_current_waits | 0       |
| Innodb_row_lock_time          | 1508173 |
| Innodb_row_lock_time_avg      | 41893   |
| Innodb_row_lock_time_max      | 51918   |
| Innodb_row_lock_waits         | 36      |
+-------------------------------+---------+
```



### 小结

- `information_schema.INNODB_TRX` 当前正在执行的事务
- `information_schema.INNODB_LOCK_WAITS` 事务的 锁等待关系
- `information_schema.INNODB_LOCKS`  当前出现的锁
- `sys.innodb_lock_waits` 视图对以上几个表进行和综合简化
- `sys.metrics` 视图 可用来查看指定指标的 统计信息，便于对参数进行合理的配置 

## 如何避免或减少锁超时的情况

- 事务不要太大，不要一股脑的往同一个事务里面方式，要思考
  - 哪些是不必要事务执行的
  - 哪些步骤是可以分步执行的
- 尽量不要在事务中放网络操作相关的东西
  - 第三方的请求的网络耗时长，会导致你的事务长时间无法结束
- ...

## Read More

- [MySQL 加锁处理分析](https://www.cnblogs.com/tutar/p/5878651.html)
- [MySql Lock wait timeout exceeded该如何处理？](https://ningyu1.github.io/site/post/75-mysql-lock-wait-timeout-exceeded/)
- [MySQL 5.7 Reference Manual](https://dev.mysql.com/doc/refman/5.7/en/) / [INFORMATION_SCHEMA Tables](https://dev.mysql.com/doc/refman/5.7/en/information-schema.html)
- [MySQL Innodb如何找出阻塞事务源头SQL](https://www.cnblogs.com/kerrycode/p/8948335.html)
- [Server Status Variable Reference](https://dev.mysql.com/doc/refman/5.7/en/server-status-variable-reference.html)