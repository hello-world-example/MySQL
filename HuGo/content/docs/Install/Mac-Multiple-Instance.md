# MAC 多实例安装 和 主从配置



## 如果已经安装 MySQL 查看安装路径在哪

```bash
# 查看 mysqld 所在目录
$ which mysqld
/usr/local/bin/mysqld

# 查看真实路径
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

# 初始化数据，--initialize-insecure是非安全模式，产生默认用户是root，密码是空
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

# 重启服务器，使用tcp(ip地址)的方式
# mysqladmin -uroot -p -P3307 -h127.0.0.1 shutdown

# mysql登录有两种方式，一种是socket，一种是tcp，使用socket登录时，会产生/tmp/mysql.sock文件，如果手动的将此文件删除，将导致无法登录， 不指定-h选项或者是-hlocalhost采用读取socket
# 文件来登录的，在多实例的情况下，容易导致读取的socket文件不存在的错误，所以建议使用-h127.0.0.1指定固定ip地址来访问

# 登陆 master
$ mysql -uroot -S /opt/mysql_3307/mysql.sock

# -p表示密码，-P表示端口，由于使用非安全模式来启动，所以建议修改密码
$ mysql -uroot -p -P3307 -h127.0.0.1

# 修改密码
mysql> SET PASSWORD FOR 'root'@'localhost' = PASSWORD('123456');
mysql> SET PASSWORD FOR 'root'@'127.0.0.1' = PASSWORD('123456');

# 给用户分配权限，此次实验使slave使用root用户拉取master上的数据，生产环境不建议分配所有权限，这里是实验环境，暂时分配所有权限
mysql> grant all privileges on *.* to root@localhost identified by '123456';
mysql> grant all privileges on *.* to root@127.0.0.1 identified by '123456';

# 刷新权限，一定一定要刷新，否则会导致同步失败等各种奇葩的问题
mysql> flush privileges; 

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
    -> master_port=3307,master_user='root',master_password='123456',
    -> master_log_file='master-bin.000001',
    -> master_log_pos=0; # 指定请求同步Master的bin-log的哪个变更之后的内容
    
mysql> start slave;

mysql> show slave status \G

# 如果显示的Slave_IO_Running和Slave_SQL_Running的状态都是yes说明，主从配置成功，只要有一个配置不成功，说明配置不成功
# Slave_IO_Running：连接到主库，并读取主库的日志到本地，生成本地日志文件
# Slave_SQL_Running:读取本地日志文件，并执行日志里的SQL命令
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



