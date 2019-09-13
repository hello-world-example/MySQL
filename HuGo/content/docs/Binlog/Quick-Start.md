#  快速入门



binlog，即二进制日志，它记录了数据库上的所有改变，并以二进制的形式保存在磁盘中；

可以用来查看数据库的 **变更历史**、**增量备份和恢复**、**主从复制** 等



## 三种格式

- `Statement` ： 基于SQL语句的复制(statement-based replication,SBR)
  - 会修改数据的 SQL 都会记录，
  - 优点：不记录每一行的变化，减少了 binlog 日志量，节约了IO，提高性能
  - 缺点：由于记录的只是执行语句，有些函数 slave 与 master 上执行结果可能不一致
- `Row` ：基于行的复制(row-based replication,RBR)
  - 保存所有被修改的记录内容
  - 优点：清楚的记录下每一行数据修改的细节，不会出 `Statement` 主从执行不一致的情况
  - 缺点：记录 SQL 执行的所有行的变更，日志相对会大很多
- `Mixed` ：混合模式复制(mixed-based replication,MBR)
  - `Statement` 与 `Row` 的结合
  - 一般的语句修改使用statment格式保存binlog，如一些函数，statement无法完成主从复制的操作，则采用row格式保存binlog，MySQL会根据执行的每一条具体的sql语句来区分对待记录的日志形式

## 查看现有配置

```mysql
mysql> show variables like 'binlog_format';
+---------------+-----------+
| Variable_name | Value     |
+---------------+-----------+
| binlog_format | STATEMENT |
+---------------+-----------+
```

## 查看是否开启binlog

```mysql
mysql> show variables like 'log_bin';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| log_bin       | OFF   |
+---------------+-------+
```

## 查看 my.cnf/my.ini 配置文件在哪

### 本地搜索

mysql-test 文件夹下面的是 MySQL 的一些测试用例

可查看 `cat ${MYSQL_HOME}/mysql-test/README` 文件详细了解

```bash
$ locate my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/include/default_my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/federated/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/ndb/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/ndb_big/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/ndb_binlog/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/ndb_rpl/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/ndb_team/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/rpl/extension/bhs/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/rpl/my.cnf
/usr/local/mysql-5.6.24-osx10.8-x86_64/mysql-test/suite/rpl_ndb/my.cnf
```

### 查看 MySQL 进程参数

MySQL 启动的时候可通过参数 `--defaults-file` 指定配置文件 

```bash
$ mysqld --help --verbose | grep "defaults-file"
--defaults-file=#       Only read default options from the given file #.
```

查看 mysqld 进程参数，如果有指定 `--defaults-file` 参数，可直接找到配置文件 位置

以下命令执行结果可看出来，并未发现有明确指定 配置文件

```bash
$ ps -ef | grep mysqld
...

... /usr/local/mysql/bin/mysqld --basedir=/usr/local/mysql --datadir=/usr/local/mysql/data --plugin-dir=/usr/local/mysql/lib/plugin --user=mysql --log-error=/usr/local/mysql/data/Kail-Mac.local.err --pid-file=/usr/local/mysql/data/Kail-Mac.local.pid

...
```

### 配置文件默认扫描路径

查看 MySQL 帮助

从命令帮助文档来看，默认的配置文件扫描顺序是

- /etc/my.cnf 
- **/etc/mysql/my.cnf** 
- /usr/local/etc/my.cnf 
- **~/.my.cnf** 「MySQL 主目录文件夹下」

```bash
$ mysqld --help --verbose | less
...

Starts the MySQL database server.

Usage: mysqld [OPTIONS]

Default options are read from the following files in the given order:
/etc/my.cnf /etc/mysql/my.cnf /usr/local/etc/my.cnf ~/.my.cnf
The following groups are read: mysqld server mysqld-5.7
The following options may be given as the first argument:
--print-defaults        Print the program argument list and exit.
--no-defaults           Don't read default options from any option file,
                        except for login file.
--defaults-file=#       Only read default options from the given file #.
--defaults-extra-file=# Read this file after the global files are read.

...
```

## 开启 binlog 日志

按照配置文件的扫描路径，这里创建 `/etc/mysql/my.cnf` 配置文件，内容如下（ 拷贝来自`~/.my.cnf` ）：

```bash
[mysqld]

# Remove leading # and set to the amount of RAM for the most important data
# cache in MySQL. Start at 70% of total RAM for dedicated server, else 10%.
# innodb_buffer_pool_size = 128M

# Remove leading # to turn on a very important data integrity option: logging
# changes to the binary log between backups.
log_bin = /usr/local/mysql/log-bin/mysql-bin
binlog_format = ROW

# These are commonly set, remove the # and set as required.
basedir = /usr/local/mysql
datadir = /usr/local/mysql/data
port = 3306
server_id = 1
```



 重启之后查看状态

```mysql
mysql> show variables like '%log_bin%';
+---------------------------------+------------------------------------------+
| Variable_name                   | Value                                    |
+---------------------------------+------------------------------------------+
| log_bin                         | ON                                       |
| log_bin_basename                | /usr/local/mysql/log-bin/mysql-bin       |
| log_bin_index                   | /usr/local/mysql/log-bin/mysql-bin.index |
| log_bin_trust_function_creators | OFF                                      |
| log_bin_use_v1_row_events       | OFF                                      |
| sql_log_bin                     | ON                                       |
+---------------------------------+------------------------------------------+
6 rows in set (0.00 sec)


mysql> show variables like 'binlog_format';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| binlog_format | ROW   |
+---------------+-------+
1 row in set (0.00 sec)
```

> Percona [在线生成 my.ini / my.conf](https://tools.percona.com/wizard)


## 查看 binlog 状态

```mysql
# 查看所有binlog日志列表
mysql> show master logs; # 或 show binary logs;
+------------------+-----------+
| Log_name         | File_size |
+------------------+-----------+
...
| mysql-bin.000004 |       143 |
| mysql-bin.000005 |       356 |
+------------------+-----------+


# 查看master状态，即最后(最新)一个binlog日志的编号名称，及其最后一个操作事件pos结束点(Position)值
mysql> show master status;
+------------------+----------+--------------+------------------+-------------------+
| File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+------------------+----------+--------------+------------------+-------------------+
| mysql-bin.000005 |      356 |              |                  |                   |
+------------------+----------+--------------+------------------+-------------------+


# 刷新log日志，产生一个新编号的binlog日志文件
mysql> flush logs;
mysql> show master logs;
+------------------+-----------+
| Log_name         | File_size |
+------------------+-----------+
...
| mysql-bin.000006 |       120 |
+------------------+-----------+

# 重置(清空)所有binlog日志，重新从 1 开始
mysql> reset master;
mysql> show master logs;
+------------------+-----------+
| Log_name         | File_size |
+------------------+-----------+
| mysql-bin.000001 |       120 |
+------------------+-----------+
```

## 查看 binlog 内容

先准备测试数据

```mysql
CREATE TABLE `MY_TEST` (
  `ID` bigint NOT NULL AUTO_INCREMENT,
  `UNAME` varchar(50) DEFAULT NULL,
  `AGE` int DEFAULT NULL,
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

insert into `test`.`MY_TEST` ( `UNAME`, `AGE`) values ( 'kail', '26');
```

### mysqlbinlog

```bash
$ ./bin/mysqlbinlog ./log-bin/mysql-bin.000001

/*!50530 SET @@SESSION.PSEUDO_SLAVE_MODE=1*/;
/*!40019 SET @@session.max_insert_delayed_threads=0*/;
/*!50003 SET @OLD_COMPLETION_TYPE=@@COMPLETION_TYPE,COMPLETION_TYPE=0*/;
DELIMITER /*!*/;
# at 4
#190301 19:51:55 server id 1  end_log_pos 120 CRC32 0xfc27ae02 	Start: binlog v 4, server v 5.6.24-log created 190301 19:51:55 at startup
# Warning: this binlog is either in use or was not closed properly.
ROLLBACK/*!*/;
BINLOG '
2xx5XA8BAAAAdAAAAHgAAAABAAQANS42LjI0LWxvZwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAADbHHlcEzgNAAgAEgAEBAQEEgAAXAAEGggAAAAICAgCAAAACgoKGRkAAQKu
J/w=
'/*!*/;
# at 120
#190301 19:56:48 server id 1  end_log_pos 429 CRC32 0xfa77cd88 	Query	thread_id=589	exec_time=0	error_code=0
use `test`/*!*/;
SET TIMESTAMP=1551441408/*!*/;
SET @@session.pseudo_thread_id=589/*!*/;
SET @@session.foreign_key_checks=1, @@session.sql_auto_is_null=0, @@session.unique_checks=1, @@session.autocommit=1/*!*/;
SET @@session.sql_mode=1075838976/*!*/;
SET @@session.auto_increment_increment=1, @@session.auto_increment_offset=1/*!*/;
/*!\C utf8mb4 *//*!*/;
SET @@session.character_set_client=45,@@session.collation_connection=45,@@session.collation_server=8/*!*/;
SET @@session.lc_time_names=0/*!*/;
SET @@session.collation_database=DEFAULT/*!*/;
CREATE TABLE `MY_TEST` (
  `ID` bigint NOT NULL AUTO_INCREMENT,
  `UNAME` varchar(50) DEFAULT NULL,
  `AGE` int DEFAULT NULL,
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8
/*!*/;
# at 429
#190301 19:57:05 server id 1  end_log_pos 509 CRC32 0x1bc8c265 	Query	thread_id=593	exec_time=0	error_code=0
SET TIMESTAMP=1551441425/*!*/;
SET @@session.time_zone='SYSTEM'/*!*/;
BEGIN
/*!*/;
# at 509
#190301 19:57:05 server id 1  end_log_pos 565 CRC32 0x4637c465 	Table_map: `test`.`my_test` mapped to number 73
# at 565
#190301 19:57:05 server id 1  end_log_pos 623 CRC32 0xa26fa710 	Write_rows: table id 73 flags: STMT_END_F

BINLOG '
ER55XBMBAAAAOAAAADUCAAAAAEkAAAAAAAEABHRlc3QAB215X3Rlc3QABAgPAxIDlgAADmXEN0Y=
ER55XB4BAAAAOgAAAG8CAAAAAEkAAAAAAAEAAgAE//ABAAAAAAAAAARrYWlsGQAAAJmigz5FEKdv
og==
'/*!*/;
# at 623
#190301 19:57:05 server id 1  end_log_pos 654 CRC32 0x02ef535a 	Xid = 73
COMMIT/*!*/;
DELIMITER ;
# End of log file
ROLLBACK /* added by mysqlbinlog */;
/*!50003 SET COMPLETION_TYPE=@OLD_COMPLETION_TYPE*/;
/*!50530 SET @@SESSION.PSEUDO_SLAVE_MODE=0*/;
```

从日志能看到创建表的 SQL 语句，但是插入语句却看不到，这是因为日志模式开启的是行模式，不记录原始SQL，只记录更新的数据。可通过添加以下参数，查看更新的数据：

```bash
$  ./bin/mysqlbinlog --base64-output=decode-rows -v ./log-bin/mysql-bin.000001
...
*!*/;
# at 509
#190301 19:57:05 server id 1  end_log_pos 565 CRC32 0x4637c465 	Table_map: `test`.`my_test` mapped to number 73
# at 565
#190301 19:57:05 server id 1  end_log_pos 623 CRC32 0xa26fa710 	Write_rows: table id 73 flags: STMT_END_F
### INSERT INTO `test`.`my_test`
### SET
###   @1=1
###   @2='kail'
###   @3=25
###   @4='2019-03-01 19:57:05'
# at 623
#190301 19:57:05 server id 1  end_log_pos 654 CRC32 0x02ef535a 	Xid = 73
COMMIT/*!*/;
...
```

> [mysql row日志格式下 查看binlog sql语句](https://www.cnblogs.com/netsa/p/7350629.html)

### show binlog events

命令格式

```mysql
mysql> show binlog events [IN 'log_name'] [FROM pos] [LIMIT [offset,] row_count];

选项解析：
    IN 'log_name'   指定要查询的binlog文件名(不指定就是第一个binlog文件)
    FROM pos        指定从哪个pos起始点开始查起(不指定就是从整个文件首个pos点开始算)
    LIMIT [offset,] 偏移量(不指定就是0)
    row_count       查询总条数(不指定就是所有行)
```



举例

```mysql
ysql> show binlog events in 'mysql-bin.000001' from 120 \G
*************************** 1. row ***************************
   Log_name: mysql-bin.000001
        Pos: 120
 Event_type: Query
  Server_id: 1
End_log_pos: 429
       Info: use `test`; CREATE TABLE `MY_TEST` (
  `ID` bigint NOT NULL AUTO_INCREMENT,
  `UNAME` varchar(50) DEFAULT NULL,
  `AGE` int DEFAULT NULL,
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8
*************************** 2. row ***************************
   Log_name: mysql-bin.000001
        Pos: 429
 Event_type: Query
  Server_id: 1
End_log_pos: 509
       Info: BEGIN
*************************** 3. row ***************************
   Log_name: mysql-bin.000001
        Pos: 509
 Event_type: Table_map
  Server_id: 1
End_log_pos: 565
       Info: table_id: 73 (test.my_test)
*************************** 4. row ***************************
   Log_name: mysql-bin.000001
        Pos: 565
 Event_type: Write_rows
  Server_id: 1
End_log_pos: 623
       Info: table_id: 73 flags: STMT_END_F
*************************** 5. row ***************************
   Log_name: mysql-bin.000001
        Pos: 623
 Event_type: Xid
  Server_id: 1
End_log_pos: 654
       Info: COMMIT /* xid=73 */
```




## Read More

- ❤❤❤ [MySQL的binlog日志](https://www.cnblogs.com/martinzhang/p/3454358.html) ❤❤❤
- [mysql binlog系列（一）----binlog介绍、日志格式、数据查看等](https://blog.csdn.net/ouyang111222/article/details/50300851)
- 官方文档 [Chapter 20 The Binary Log](https://dev.mysql.com/doc/internals/en/binary-log.html)