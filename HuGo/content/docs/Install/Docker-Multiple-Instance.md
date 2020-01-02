# 使用 Docker 搭建主从测试



>  假设Docker 宿主机： 192.168.1.5



## Docker 搭建 MySQL 主从环境

```mysql
# Master
ᐅ docker run -d -e MYSQL_ROOT_PASSWORD=123456 \
  --name mysql_3307 \
  -p3307:3306 mysql:5.7 \
  --server_id=1 --log-bin=master-bin

# Slave
ᐅ docker run -d -e MYSQL_ROOT_PASSWORD=123456 \
  --name mysql_3308 \
  -p3308:3306 mysql:5.7 \
  --server_id=2 --relay-log=slave-relay-bin
 
# 进入 Master
ᐅ docker exec -it mysql_3307 mysql -p123456

mysql> show master status;
+-------------------+----------+--------------+------------------+-------------------+
| File              | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+-------------------+----------+--------------+------------------+-------------------+
| master-bin.000003 |      154 |              |                  |                   |
+-------------------+----------+--------------+------------------+-------------------+
1 row in set (0.00 sec)

-- 创建数据库
mysql> create database test;
Query OK, 1 row affected (0.00 sec)
 
 
# 进入 Slave
ᐅ docker exec -it mysql_3308 mysql -p123456

mysql> change master to master_host='192.168.1.5',
                        master_port=3307,
                        master_user='root',master_password='123456',
                        master_log_file='master-bin.000003',
                        master_log_pos=154;
Query OK, 0 rows affected, 2 warnings (0.05 sec)
 
mysql> start slave;
Query OK, 0 rows affected (0.02 sec)
 
mysql> show slave status \G
*************************** 1. row ***************************
               Slave_IO_State: Waiting for master to send event
                  Master_Host: 192.168.1.5
                  Master_User: root
                  Master_Port: 3307
                Connect_Retry: 60
              Master_Log_File: master-bin.000003
          Read_Master_Log_Pos: 154
               Relay_Log_File: slave-relay-bin.000002
                Relay_Log_Pos: 321
        Relay_Master_Log_File: master-bin.000003
             Slave_IO_Running: Yes
            Slave_SQL_Running: Yes
...
```



## Read More

- https://hub.docker.com/_/mysql

