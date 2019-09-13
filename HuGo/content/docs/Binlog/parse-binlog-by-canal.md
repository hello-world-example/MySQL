# Canal 订阅 binlog



## 安装 Canal

```bash
$ git clone https://github.com/alibaba/canal.git
$ cd canal

# 编译打包
$ mvn clean install -Dmaven.test.skip -Denv=release

#
$ cd target
$ mkdir /opt/websuite/canal.deployer
$ cp canal.deployer-1.1.3-SNAPSHOT.tar.gz /opt/websuite/canal.deployer/
$ cd /opt/websuite/canal.deployer
$ tar zxvf canal.deployer-1.1.3-SNAPSHOT.tar.gz

$ tree conf/
conf/
├── canal.properties
├── example
│   └── instance.properties
├── logback.xml
├── metrics
│   └── Canal_instances_tmpl.json
└── spring
    ├── base-instance.xml
    ├── default-instance.xml
    ├── file-instance.xml
    ├── group-instance.xml
    ├── memory-instance.xml
    └── tsdb
        ├── h2-tsdb.xml
        ├── mysql-tsdb.xml
        ├── sql
        │   └── create_table.sql
        └── sql-map
            ├── sqlmap-config.xml
            ├── sqlmap_history.xml
            └── sqlmap_snapshot.xml

# canal.instance.master.address=数据库地址
# canal.instance.dbUsername=用户名
# canal.instance.dbPassword=密码
# canal.instance.defaultDatabaseName=数据库名
$ vim conf/example/instance.properties

# 启动
$ ./bin/startup.sh 

# 查看日志
$ tail -fn 400 logs/canal/canal.log
$ tail -fn 400 logs/example/example.log

# 关闭
$ ./bin/stop.sh
```

> 来自：https://github.com/alibaba/canal/wiki/QuickStart

## Canal 示例

```bash
# 省略编译打包部分


$ cd target
$ mkdir /opt/websuite/canal.example
$ cp canal.example-1.1.3-SNAPSHOT.tar.gz /opt/websuite/canal.example/
$ cd /opt/websuite/canal.example
$ tar zxvf canal.example-1.1.3-SNAPSHOT.tar.gz


# 启动
$ ./bin/startup.sh 

# 查看日志
$ tail -fn 400 logs/example/entry.log

# 关闭
$ ./bin/stop.sh
```

> 来自：https://github.com/alibaba/canal/wiki/ClientExample



```bash
# 省略编译打包部分


$ cd target
$ mkdir /opt/websuite/canal.adapter
$ cp canal.adapter-1.1.3-SNAPSHOT.tar.gz /opt/websuite/canal.adapter/
$ cd /opt/websuite/canal.adapter
$ tar zxvf canal.adapter-1.1.3-SNAPSHOT.tar.gz


# 启动
$ ./bin/startup.sh 

# 查看日志
$ tail -fn 400 logs/example/entry.log

# 关闭
$ ./bin/stop.sh
```



## Read More

- [QuickStart](https://github.com/alibaba/canal/wiki/QuickStart)
- [Canal Kafka RocketMQ QuickStart](https://github.com/alibaba/canal/wiki/Canal-Kafka-RocketMQ-QuickStart)
- 