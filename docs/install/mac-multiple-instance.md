# MAC 多实例安装 和 主从配置



## 如果已经安装 MySQL 查看安装路径在哪

```bash
# 查看 mysqld 所在目录
$ which mysqld
/usr/local/bin/mysqld

# 查看真是路径
$ ll /usr/local/bin/mysqld
lrwxr-xr-x  ... /usr/local/bin/mysqld -> ../Cellar/mysql/5.7.17/bin/mysqld

$ cd /usr/local/Cellar/mysql/5.7.17/
$ pwd
/usr/local/Cellar/mysql/5.7.17
```

## 启动多实例

```bash
# 创建两个目录
$ mkdir -p /opt/mysql_3307
$ mkdir -p /opt/mysql_3308

$ vim /opt/mysql_3307/my.cnf
# 内容如下
[mysqld]
basedir = /opt/mysql_3307
datadir = /opt/mysql_3307/data
port = 3307
server_id = 1
socket = /opt/mysql_3307/mysql.sock

$ vim /opt/mysql_3308/my.cnf
# 内容如下
[mysqld]
basedir = /opt/mysql_3308
datadir = /opt/mysql_3308/data
port = 3308
server_id = 2
socket = /opt/mysql_3308/mysql.sock

# 初始化数据
$ mysqld --defaults-file=/opt/mysql_3307/my.cnf --initialize-insecure
$ mysqld --defaults-file=/opt/mysql_3308/my.cnf --initialize-insecure

# 启动 MySQL
$ mysqld --defaults-file=/opt/mysql_3307/my.cnf &
$ mysqld --defaults-file=/opt/mysql_3308/my.cnf &
```

> - [2.10.1 Initializing the Data Directory 官方文档](https://dev.mysql.com/doc/refman/5.7/en/data-directory-initialization.html)
> - [4.3.2 mysqld_safe — MySQL Server Startup Script](https://dev.mysql.com/doc/refman/5.7/en/mysqld-safe.html)



## 创建主从关系

```bash
$ vim /opt/mysql_3307/my.cnf
# 新增
log_bin = master-bin
binlog_format = ROW

$ vim /opt/mysql_3308/my.cnf
# 新增
relay-log=slave-relay-bin



# 登陆 master
$ mysql -uroot -S /opt/mysql_3307/mysql.sock

mysql> show master status;
+-------------------+----------+--------------+------------------+-------------------+
| File              | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+-------------------+----------+--------------+------------------+-------------------+
| master-bin.000001 |      154 |              |                  |                   |
+-------------------+----------+--------------+------------------+-------------------+
1 row in set (0.00 sec)


# 登陆 salve
$ mysql -uroot -S /opt/mysql_3308/mysql.sock

# change master to master_host='127.0.0.1',master_port=3307,master_user='root',master_password='',master_log_file='master-bin.000001',master_log_pos=0;
mysql> change master to master_host='127.0.0.1',
    -> master_port=3307,master_user='root',master_password='',
    -> master_log_file='master-bin.000001',
    -> master_log_pos=0; # 指定请求同步Master的bin-log的哪个变更之后的内容
    
mysql> start slave;

mysql> show slave status;              
```



## 主从复制之前数据同步

```bash
# 从库上停止 复制
$ mysql> stop slave;

# dump 主库数据，--master-data=1 包含 change master to 语句
$ mysqldump -uroot -p --all-databases --master-data=1 -S /opt/mysql_3307/mysql.sock > /tmp/master1.sql

# 数据导入 从库
$ mysql -uroot  -S /opt/mysql_3308/mysql.sock < /tmp/master1.sql

# 从库上开启 复制
$ mysql> start slave;
```





## 注意事项

- 从库一般禁止修改，否则主从复制会报错，从库一般需要创建只读的账户
- 如果主从复制报错，`stop slave;`  然后全量同步数据，之后重新制定主库，然后 `start slave;`



## Read More

- [MySQL mysqldump数据导出详解](https://www.cnblogs.com/chenmh/p/5300370.html)
- [mysqldump的--master-data参数](https://blog.csdn.net/seteor/article/details/17263509)
- [mysqldump中master-data和dump-slave的区别](http://blog.chinaunix.net/uid-451-id-3143431.html)



