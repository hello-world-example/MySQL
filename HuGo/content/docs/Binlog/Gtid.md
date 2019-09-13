# GTID

GTID (Global Transaction ID) 是 MySQL5.6 引入的功能，可以在集群全局范围标识事务，**用于取代过去通过binlog 文件偏移量定位复制位置的传统方式**。

借助GTID，在发生主备切换的情况下，MySQL的其它Slave可以自动在新主上找到正确的复制位置，这**大大简化了复杂复制拓扑下集群的维护，也减少了人为设置复制位置发生误操作的风险**。另外，基于GTID的复制可以忽略已经执行过的事务，减少了数据发生不一致的风险。

GTID虽好，要想运用自如还需充分了解其原理与特性，特别要注意与**传统的基于binlog文件偏移量复制方式**不一样的地方。



## GTID长什么样

根据官方文档定义，GTID由 `source_id` 加 `transaction_id` 构成。

```
GTID = source_id:transaction_id 
```

上面的 `source_id` 指示发起事务的 MySQL 实例，值为该实例的 `server_uuid`。`server_uuid` 由MySQL在第一次启动时自动生成并被持久化到 `auto.cnf` 文件里，`transaction_id` 是MySQL实例上执行的事务序号，**从 1 开始递增**。 例如：

```
e6954592-8dba-11e6-af0e-fa163e1cf111:1 
```

一组连续的事务可以用 `'-'` 连接的事务序号范围表示。例如

```
e6954592-8dba-11e6-af0e-fa163e1cf111:1-5 
```

更一般的情况是GTID的集合。GTID集合可以包含来自多个 `source_id` 的事务，它们之间用逗号分隔；如果来自同一 `source_id` 的事务序号有多个范围区间，各组范围之间用冒号分隔，例如：

```
e6954592-8dba-11e6-af0e-fa163e1cf111:1-5:11-18,e6954592-8dba-11e6-af0e-fa163e1cf3f2:1-27 
```

GTID集合拥有如下的形式定义：

```mysql
gtid_set:
    uuid_set [, uuid_set] ...
    | ''

uuid_set:
    uuid:interval[:interval]...

uuid:
    hhhhhhhh-hhhh-hhhh-hhhh-hhhhhhhhhhhh

h:
    [0-9|A-F]

interval:
    n[-n]

    (n >= 1)
```



## 如何查看GTID

可以通过MySQL的几个变量查看相关的GTID信息。

- `gtid_executed` 在当前实例上执行过的GTID集合; 实际上包含了所有记录到binlog中的事务。所以，设置`set sql_log_bin=0` 后执行的事务不会生成 binlog 事件，也不会被记录到 gtid_executed 中。执行RESET MASTER 可以将该变量置空。
- `gtid_purged` binlog不可能永远驻留在服务上，需要定期进行清理，通过 `expire_logs_days` 可以控制定期清理间隔，否则迟早它会把磁盘用尽。**gtid_purged 用于记录已经被清除了的binlog事务集合，它是gtid_executed的子集**。只有 gtid_executed 为空时才能手动设置该变量，此时会同时更新 gtid_executed 为和gtid_purged相同的值。gtid_executed 为空意味着要么之前没有启动过基于GTID的复制，要么执行过RESET MASTER。执行 RESET MASTER 时同样也会把 gtid_purged 置空，即始终保持 gtid_purged 是gtid_executed 的子集。
- `gtid_next` 会话级变量，指示如何产生下一个GTID。可能的取值如下:
  - `AUTOMATIC` 自动生成下一个GTID，实现上是分配一个当前实例上尚未执行过的序号最小的GTID。
  - `ANONYMOUS` 设置后执行事务不会产生GTID。
    - `显式指定的GTID` 可以指定任意形式合法的GTID值，但不能是当前gtid_executed中的已经包含的GTID，否则，下次执行事务时会报错。

这些变量可以通过show命令查看，比如

```mysql
mysql> show global variables like 'gtid%';
+----------------------+------------------------------------------+
| Variable_name        | Value                                    |
+----------------------+------------------------------------------+
| gtid_deployment_step | OFF                                      |
| gtid_executed        | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-6 |
| gtid_mode            | ON                                       |
| gtid_owned           |                                          |
| gtid_purged          |                                          |
+----------------------+------------------------------------------+
5 rows in set (0.02 sec)

mysql> show  variables like 'gtid_next';
+---------------+-----------+
| Variable_name | Value     |
+---------------+-----------+
| gtid_next     | AUTOMATIC |
+---------------+-----------+
1 row in set (0.00 sec) 
```



## 如何产生GTID

GTID 的生成受 `gtid_next` 控制。 在 Master 上，`gtid_next` 是默认的 `AUTOMATIC`，即在每次事务提交时自动生成新的 GTID。它从当前已执行的GTID集合（即 `gtid_executed`）中，找一个大于0的未使用的最小值作为下个事务GTID。同时在 binlog 的实际的更新事务事件前面插入一条 `set gtid_next` 事件。

以下是一条 insert 语句生成的 binlog 记录

```mysql
mysql> use `test`
Database changed

mysql> insert into tbx1 values(1);
Query OK, 1 row affected (0.01 sec)

mysql> show binlog events IN 'binlog.000015';
+---------------+-----+----------------+-----------+-------------+-------------------------------------------------------------------+
| Log_name      | Pos | Event_type     | Server_id | End_log_pos | Info                                                              |
+---------------+-----+----------------+-----------+-------------+-------------------------------------------------------------------+
...
| binlog.000015 | 707 | Gtid           |         1 |         755 | SET @@SESSION.GTID_NEXT= 'e10c75be-5c1b-11e6-ab7c-000c296078ae:9' |
| binlog.000015 | 755 | Query          |         1 |         834 | BEGIN                                                             |
| binlog.000015 | 834 | Query          |         1 |         934 | use `test`; insert into tbx1 values(1)                            |
| binlog.000015 | 934 | Xid            |         1 |         965 | COMMIT /* xid=20 */                                               | 
...
```

**在 Slave 上回放主库的 binlog 时，先执行 `set gtid_next …`，然后再执行真正的 insert 语句，确保在主和备上这条 insert 对应于相同的 GTID。**

一般情况下，GTID集合是连续的，但使用多线程复制(MTS)以及通过gtid_next进行人工干预时会导致gtid空洞。比如下面这样:

```mysql
mysql> show master status;
+---------------+----------+--------------+------------------+------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                        |
+---------------+----------+--------------+------------------+------------------------------------------+
| binlog.000015 |      965 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-9 |
+---------------+----------+--------------+------------------+------------------------------------------+
1 row in set (0.00 sec)

mysql> set gtid_next='e10c75be-5c1b-11e6-ab7c-000c296078ae:12';
Query OK, 0 rows affected (0.00 sec)

mysql> begin;
Query OK, 0 rows affected (0.00 sec)

mysql> commit;
Query OK, 0 rows affected (0.00 sec)

mysql> set gtid_next='AUTOMATIC';
Query OK, 0 rows affected (0.00 sec)

mysql> show master status;
+---------------+----------+--------------+------------------+---------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                           |
+---------------+----------+--------------+------------------+---------------------------------------------+
| binlog.000015 |     1158 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-9:12 |
+---------------+----------+--------------+------------------+---------------------------------------------+
1 row in set (0.00 sec) 
```

继续执行事务，MySQL会分配一个最小的未使用GTID,也就是从出现空洞的地方分配GTID，最终会把空洞填上。

```mysql
mysql> insert into tbx1 values(1);
Query OK, 1 row affected (0.01 sec)

mysql> show master status;
+---------------+----------+--------------+------------------+----------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                            |
+---------------+----------+--------------+------------------+----------------------------------------------+
| binlog.000015 |     1416 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-10:12 |
+---------------+----------+--------------+------------------+----------------------------------------------+
1 row in set (0.00 sec) 
```

这意味着**严格来说我们即不能假设GTID集合是连续的，也不能假定GTID序号大的事务在GTID序号小的事务之后执行，事务的顺序应由事务记录在binlog中的先后顺序决定**。

## GTID限制

- 不支持非事务引擎

  不支持非事务引擎 不是 不能建立 MyISAM 表、对 MyISAM 表DML，InnoDB 中一个事务中可以是多条 SQL，MyISAM 中，一条DML生成一个GTID号，所以说不支持事务

  ```mysql
  -- MyISAM 每条 SQL 自动提交，所以 MyISAM 中不存在事务的概念
  mysql> insert into user_myisam values (3,100);   
  
  Executed_Gtid_Set: 59fe7a3e-9dd6-11e7-9d6c-000c29e57c69:1-68 
  ```

- 不支持 `create table … select`  语句复制，主库会直接报错

  ```mysql
  mysql> create table t1 select * from user_innodb;
  
  ERROR 1786 (HY000): CREATE TABLE ... SELECT is forbidden when @@GLOBAL.ENFORCE_GTID_CONSISTENCY = 1.
  ```

- 不允许在一个SQL同时更新一个 事务引擎 和 非事务引擎 的表

  ```mysql
  mysql> begin;
  
  mysql> update user_innodb set money=100 where id=1;
  
  mysql> update user_myisam set money=100 where id=1;
  
  ERROR 1785 (HY000): When @@GLOBAL.ENFORCE_GTID_CONSISTENCY = 1, updates to non-transactional tables can only be done in either autocommitted statements or single-statement transactions, and never in the same statement as updates to transactional tables.
  ```

- 在一个复制组中，必须要求统一开启GTID或是关闭GTID
- 对于 `create temporary table` 和 `drop temporary table` 语句不支持
- 不支持 `sql_slave_skip_counter`



## GTID的持久化

GTID相关的信息存储在binlog文件中，为此 MySQL5.6 新增了下面 2个 binlog 事件。

- `Previous_gtids_log_event` 在每个binlog文件的开头部分，记录在该binlog文件之前已执行的GTID集合。
- `Gtid_log_event`  即前面看到的 `set gtid_next …`,它出现在每个事务的前面，表明下一个事务的gtid。

示例如下:

```mysql
mysql> show binlog events IN 'binlog.000015';
+---------------+-----+----------------+-----------+-------------+-------------------------------------------------------------------+
| Log_name      | Pos | Event_type     | Server_id | End_log_pos | Info                                                              |
+---------------+-----+----------------+-----------+-------------+-------------------------------------------------------------------+
| binlog.000015 |   4 | Format_desc    |         1 |         120 | Server ver: 5.6.31-77.0-log, Binlog ver: 4                        |
| binlog.000015 | 120 | Previous_gtids |         1 |         191 | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-6                          |
| binlog.000015 | 191 | Gtid           |         1 |         239 | SET @@SESSION.GTID_NEXT= 'e10c75be-5c1b-11e6-ab7c-000c296078ae:7' |
| binlog.000015 | 239 | Query          |         1 |         318 | BEGIN                                                             |
| binlog.000015 | 318 | Query          |         1 |         418 | use `test`; insert into tbx1 values(1)                            |
| binlog.000015 | 418 | Xid            |         1 |         449 | COMMIT /* xid=13 */                                               |
| binlog.000015 | 449 | Gtid           |         1 |         497 | SET @@SESSION.GTID_NEXT= 'e10c75be-5c1b-11e6-ab7c-000c296078ae:8' |
| binlog.000015 | 497 | Query          |         1 |         576 | BEGIN                                                             |
| binlog.000015 | 576 | Query          |         1 |         676 | use `test`; insert into tbx1 values(1)                            |
| binlog.000015 | 676 | Xid            |         1 |         707 | COMMIT /* xid=17 */                                               |
| binlog.000015 | 707 | Gtid           |         1 |         755 | SET @@SESSION.GTID_NEXT= 'e10c75be-5c1b-11e6-ab7c-000c296078ae:9' |
| binlog.000015 | 755 | Query          |         1 |         834 | BEGIN                                                             |
| binlog.000015 | 834 | Query          |         1 |         934 | use `test`; insert into tbx1 values(1)                            |
| binlog.000015 | 934 | Xid            |         1 |         965 | COMMIT /* xid=20 */                                               |
+---------------+-----+----------------+-----------+-------------+-------------------------------------------------------------------+
14 rows in set (0.00 sec) 
```

MySQL服务器启动时，通过读binlog文件，初始化 `gtid_executed` 和 `gtid_purged` ,使它们的值能和上次MySQL运行时一致。

- `gtid_executed` 被设置为最新的 binlog 文件中 `Previous_gtids_log_event` 和所有 `Gtid_log_event` 的并集。
- `gtid_purged` 为最老的binlog文件中 `Previous_gtids_log_event`。

由于这两个重要的变量值记录在binlog中，所以开启 `gtid_mode` 时必须同时在主库上开启 `log_bin` 在备库上开启 `log_slave_updates`。但是，在MySQL5.7中没有这个限制。MySQL5.7中，新增加一个系统表`mysql.gtid_executed` 用于持久化已执行的GTID集合。当主库上没有开启 log_bin 或在备库上没有开启log_slave_updates 时，mysql.gtid_executed 会跟用户事务一起每次更新。否则只在binlog日志发生rotation时更新mysql.gtid_executed。

## 如何配置基于GTID的复制

MySQL服务器的my.cnf配置文件中增加GTID相关的参数

```bash
log_bin                        = /mysql/binlog/mysql_bin
log_slave_updates              = true
gtid_mode                      = ON 
enforce_gtid_consistency       = true 
relay_log_info_repository      = TABLE
relay_log_recovery             = ON 
```

然后在Slave上指定 `MASTER_AUTO_POSITION = 1` 执行 `CHANGE MASTER TO` 即可。比如:

```sql
CHANGE MASTER TO MASTER_HOST='node1',MASTER_USER='repl',MASTER_PASSWORD='repl',MASTER_AUTO_POSITION=1; 
```

## 基于GTID的复制如何工作

在 `MASTER_AUTO_POSITION = 1` 的情况下 ，MySQL会使用 `COM_BINLOG_DUMP_GTID` 协议进行复制。过程如下:

备库发起复制连接时，将自己的已接受和已执行的 `gtids` 的并集(后面称为`slave_gtid_executed`)发送给主库。即下面的集合:

```
UNION(@@global.gtid_executed, Retrieved_gtid_set - last_received_GTID) 
```

主库将自己的 `gtid_executed` 与 `slave_gtid_executed` 的差集的 binlog 发送给 Slave。主库的 binlog dump 过程如下：

1. 检查 `slave_gtid_executed` 是否是主库 `gtid_executed` 的子集，如否那么主备数据可能不一致，报错。
2. 检查主库的 `purged_executed` 是否是 `slave_gtid_executed` 的子集，如否代表缺失备库需要的binlog，报错
3. 从最后一个 Binlog 开始扫描，获取文件头部的 `PREVIOUS_GTIDS_LOG_EVENT` ，如果它是`slave_gtid_executed` 的子集，则这是需要发送给Slave的第一个binlog文件，否则继续向前扫描。
4. 从第3步找到的binlog文件的开头读取binlog记录，判断binlog记录是否已被包含在 `slave_gtid_executed`中，如果已包含跳过不发送。

从上面的过程可知，在指定 `MASTER_AUTO_POSITION = 1`时，Master发送哪些binlog记录给Slave，取决于Slave的 `gtid_executed` 和 `Retrieved_Gtid_Set` 以及Master的 `gtid_executed`，和 `relay_log_info` 以及`master_log_info` 中保存的复制位点没有关系。

## 如何修复复制错误

在基于GTID的复制拓扑中，要想修复 Slave 的 SQL 线程错误，过去的 `SQL_SLAVE_SKIP_COUNTER` 方式不再适用。需要通过设置 `gtid_next` 或 `gtid_purged` 完成，当然前提是已经确保主从数据一致，仅仅需要跳过复制错误让复制继续下去。比如下面的场景：

在从库上创建表 `tb1`

```mysql
mysql> set sql_log_bin=0;
Query OK, 0 rows affected (0.00 sec)

mysql> create table tb1(id int primary key,c1 int);
Query OK, 0 rows affected (1.06 sec)

mysql> set sql_log_bin=1;
Query OK, 0 rows affected (0.00 sec) 
```

在主库上创建表tb1

```mysql
mysql> create table tb1(id int primary key,c1 int);
Query OK, 0 rows affected (1.06 sec) 
```

由于从库上这个表已经存在，从库的复制SQL线程出错停止。

```mysql
mysql> show slave status\G
*************************** 1. row ***************************
               Slave_IO_State: Waiting for master to send event
                  Master_Host: 192.168.125.134
                  Master_User: sn_repl
                  Master_Port: 3306
                Connect_Retry: 60
              Master_Log_File: binlog.000001
          Read_Master_Log_Pos: 1422
               Relay_Log_File: mysqld-relay-bin.000003
                Relay_Log_Pos: 563
        Relay_Master_Log_File: binlog.000001
             Slave_IO_Running: Yes
            Slave_SQL_Running: No
              Replicate_Do_DB: 
          Replicate_Ignore_DB: 
           Replicate_Do_Table: 
       Replicate_Ignore_Table: 
      Replicate_Wild_Do_Table: 
  Replicate_Wild_Ignore_Table: 
                   Last_Errno: 1050
                   Last_Error: Error 'Table 'tb1' already exists' on query. Default database: 'test'. Query: 'create table tb1(id int primary key,c1 int)'
                 Skip_Counter: 0
          Exec_Master_Log_Pos: 1257
              Relay_Log_Space: 933
              Until_Condition: None
               Until_Log_File: 
                Until_Log_Pos: 0
           Master_SSL_Allowed: No
           Master_SSL_CA_File: 
           Master_SSL_CA_Path: 
              Master_SSL_Cert: 
            Master_SSL_Cipher: 
               Master_SSL_Key: 
        Seconds_Behind_Master: NULL
Master_SSL_Verify_Server_Cert: No
                Last_IO_Errno: 0
                Last_IO_Error: 
               Last_SQL_Errno: 1050
               Last_SQL_Error: Error 'Table 'tb1' already exists' on query. Default database: 'test'. Query: 'create table tb1(id int primary key,c1 int)'
  Replicate_Ignore_Server_Ids: 
             Master_Server_Id: 1
                  Master_UUID: e10c75be-5c1b-11e6-ab7c-000c296078ae
             Master_Info_File: mysql.slave_master_info
                    SQL_Delay: 0
          SQL_Remaining_Delay: NULL
      Slave_SQL_Running_State: 
           Master_Retry_Count: 86400
                  Master_Bind: 
      Last_IO_Error_Timestamp: 
     Last_SQL_Error_Timestamp: 161203 15:14:17
               Master_SSL_Crl: 
           Master_SSL_Crlpath: 
           Retrieved_Gtid_Set: e10c75be-5c1b-11e6-ab7c-000c296078ae:5-6
            Executed_Gtid_Set: e10c75be-5c1b-11e6-ab7c-000c296078ae:1-5
                Auto_Position: 1
1 row in set (0.00 sec) 
```

从上面的输出可以知道，从库已经执行过的事务是 `e10c75be-5c1b-11e6-ab7c-000c296078ae:1-5`，执行出错的事务是 `e10c75be-5c1b-11e6-ab7c-000c296078ae:6`，当前主备的数据其实是一致的，可以通过设置 `gtid_next`跳过这个出错的事务。

在从库上执行以下SQL:

```mysql
mysql> set gtid_next='e10c75be-5c1b-11e6-ab7c-000c296078ae:6';
Query OK, 0 rows affected (0.00 sec)

mysql> begin;
Query OK, 0 rows affected (0.00 sec)

mysql> commit;
Query OK, 0 rows affected (0.00 sec)

mysql> set gtid_next='AUTOMATIC';
Query OK, 0 rows affected (0.00 sec)

mysql> start slave;
Query OK, 0 rows affected (0.02 sec) 
```

设置 `gtid_next` 的方法一次只能跳过一个事务，要批量的跳过事务可以通过设置 `gtid_purged` 完成。假设下面的场景：

主库上已执行的事务

```mysql
mysql> show master status;
+---------------+----------+--------------+------------------+-------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                         |
+---------------+----------+--------------+------------------+-------------------------------------------+
| binlog.000001 |     2364 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-10 |
+---------------+----------+--------------+------------------+-------------------------------------------+
1 row in set (0.00 sec) 
```

从库上已执行的事务

```mysql
mysql> show master status;
+---------------+----------+--------------+------------------+------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                        |
+---------------+----------+--------------+------------------+------------------------------------------+
| binlog.000001 |     1478 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-6 |
+---------------+----------+--------------+------------------+------------------------------------------+
1 row in set (0.00 sec) 
```

假设经过修复从库已经和主库的数据一致了，但由于复制错误Slave的SQL线程依然处于停止状态。现在可以通过把从库的 `gtid_purged` 设置为和主库的 `gtid_executed` 一样跳过不一致的GTID使复制继续下去，步骤如下。

在从库上执行

```mysql
mysql> reset master;
Query OK, 0 rows affected (0.01 sec)

mysql> set GLOBAL gtid_purged='e10c75be-5c1b-11e6-ab7c-000c296078ae:1-10';
Query OK, 0 rows affected (0.03 sec)

mysql> show master status;
+---------------+----------+--------------+------------------+-------------------------------------------+
| File          | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set                         |
+---------------+----------+--------------+------------------+-------------------------------------------+
| binlog.000002 |      191 |              |                  | e10c75be-5c1b-11e6-ab7c-000c296078ae:1-10 |
+---------------+----------+--------------+------------------+-------------------------------------------+
1 row in set (0.00 sec) 
```

此时从库的Executed_Gtid_Set已经包含了主库上'1-10'的事务，再开启复制会从后面的事务开始执行，就不会出错了。

```mysql
mysql> start slave;
Query OK, 0 rows affected (0.01 sec) 
```

使用gtid_next和gtid_purged修复复制错误的前提是，跳过那些事务后仍可以确保主备数据一致。如果做不到，就要考虑pt-table-sync或者拉备份的方式了。



## Read More

- 官方文档 [16.1.3.1 GTID Format and Storage](https://dev.mysql.com/doc/refman/5.6/en/replication-gtids-concepts.html)

- [MySQL传统复制与GTID复制的原理阐述](https://blog.csdn.net/wukong_666/article/details/79311743)

- `GTID site:mysql.taobao.org`

- 本文主要来自 [MySQL的GTID复制比传统复制的优势](https://blog.csdn.net/anzhen0429/article/details/77658663)
  - GTID与备份恢复
    - 通过mysqldump进行备份
    - 通过Xtrabackup进行备份
  - GTID与MHA
  - GTID与crash safe slave
    - 基于binlog文件位置的复制
    - 基于GTID的复制
    - 设置"双1"对性能的影响
    - 如何在非"双1"下保证crash safe slave
    - MTS下特有的问题
    - Master的crash safe

- 

  