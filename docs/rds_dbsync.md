# rds_dbsync



## 非 Linux环境下如何使用

MAC 下无法执行编译好的可执行文件，可以通过 Docker 运行

```bash
$ docker pull centos

# 运行
$ docker run -t -d --net=host --name=centos centos
# 进入容器
$ docker exec -it centos bash

[docker]$ yum update
[docker]$ yum install wget
[docker]$ yum install vim
[docker]$ cd ~
[docker]$ wget https://github.com/aliyun/rds_dbsync/files/919279/mysql2pgsql.bin.el6.20170413.tar.gz
[docker]$ tar zxvf mysql2pgsql.bin.el6.20170413.tar.gz
[docker]$ cd /root/mysql2pgsql.bin.el6.20170413/bin
# 运行
[docker]$ ./mysql2pgsql -d
```

> - [如何设置PostgreSQL远程访问](https://blog.csdn.net/caoshiying/article/details/53317943)



## 修改配置文件

```bash
[src.mysql]
host = "192.168.1.6"
port = "3307"
user = "kail"
password = "1723"
db = "test"

... 省略无需关注的配置

[desc.pgsql]
connect_string = "host=192.168.1.6 dbname=postgres port=5432 user=postgres password=postgres"

... 省略无需关注的配置
```

## mysql2pgsql 参数

- `-d` 可选参数，只生成目的表的建表DDL语句，不实际进行数据同步
- `-s <schema>` 可选参数，指定 `schema`，一次命令只能指定一个 `schema`。如果不指定此参数，则数据会导入到 `public` 下
- 
- `-n` 可选参数，需要与-d一起使用，指定在DDL语句中不包含表分区定义


- `-j <threads>` 可选参数，指定使用多少线程进行数据同步；如果不指定此参数，会使用5个线程并发
- `-l <tables_list_file>` 可选参数，指定一个文本文件，文件中含有需要同步的表；如果不指定此参数，则同步配置文件中指定数据库下的所有表。文件格式和内容如下：
```
table1 : select * from table_big where column1 < '2016-08-05'
table2 : 
table3
table4: select column1, column2 from tableX where column1 != 10
table5: select * from table_big where column1 >= '2016-08-05'
```




## 常用命令

```bash
# 获取目的端(MySQL)对应表的 DDL，获取之后需要先在 Postgres 上创建好对应的 schema 和 table
$ ./mysql2pgsql -d

# 数据同步到 test schema 下
$ ./mysql2pgsql -s test
```

## 工具缺陷

- Postgres 必须事先创建好 对应的 scheme 和 table，可通过 `./mysql2pgsql -d` 获取 DDL 语句
- 无法指定 配置文件
  - [增加了一个-c的参数，指定配置文件 #119](https://github.com/aliyun/rds_dbsync/pull/119)

## Read More

- [rds_dbsync](https://github.com/aliyun/rds_dbsync)
- [mysql2pgsql 官方文档](https://github.com/aliyun/rds_dbsync/blob/master/doc/mysql2pgsql_ch.md)
- [MySQL迁移数据到HybridDB for PostgreSQL](https://help.aliyun.com/document_detail/35458.html)