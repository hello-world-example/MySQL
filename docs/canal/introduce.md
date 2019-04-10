# canal简介

## canal介绍
Canal是阿里巴巴的开源软件，它主要是一个应用和mysql之间的数据同步中间件。类似Mq等这样的消息中间件，但他不需要借助其他的系统发消息，他是直接监听Mysql数据库，它伪装成mysql的从库通过对binlog日志的解析从而实现了数据库增删改查的监听

## 主要的业务场景
- 数据库备份
- 搜索引擎索引更新&建立
- 业务缓存的更新
- 充当消息组件（订单变更，商品资料变更等）


## 工作原理
1. canal模拟mysql slave的交互协议，伪装自己为mysql slave，向mysql master发送dump协议
2. ​mysql master收到dump请求，开始推送binary log给slave(也就是canal)
3. canal解析binary log对象(原始为byte流)

## Canal环境部署
- 安装canal
```$bash

方式一
$ git clone https://github.com/alibaba/canal.git
$ cd canal

# 编译打包
$ mvn clean install -Dmaven.test.skip -Denv=release

# ${项目路径}是你本地项目路径，下面的目录就是操作的主目录
$ cd target
$ mkdir deployer
$ tar -xf canal.deployer-1.1.3.tar.gz -C deployer
$ mkdir adapter
$ tar -xf canal.adapter-1.1.3.tar.gz -C adapter
$ mkdir example
$ tar -xf canal.example-1.1.3.tar.gz -C example

方式二
1. 打开浏览器，访问https://github.com/alibaba/canal
2. 点击页面的realse按钮
3. 选择页面中assets列表中canal.deployer-1.1.3.tar.gz，进行下载
4. tar -xf canal.deployer-1.1.3.tar.gz进行解压

```
- canal重要配置文件详解
```$bash
# 进入主目录
$ cd target/canal

# canal.properties 重要参数解释

# canal自己的serverId，无实际意义
canal.id = 5000
# canal服务本机ip
canal.ip = 127.0.0.1
# canal服务本机端口
canal.port = 11111
# canal监听的mysql名称，这个名称可以自定义，可配置多个，用逗号分隔，配置一个就会有一个以配置名称为名的文件夹
# 例如此时，就会有一个example文件夹，这是一一对应的
canal.destinations = example

$ cd example/

$ instance.properties 重要参数解释
# canal伪装成slave的id，不能与主从配置中的机器一样
canal.instance.mysql.slaveId=1024
# canal访问master的ip和端口
canal.instance.master.address=127.0.0.1:3307
# canal访问master的binlog
canal.instance.master.journal.name=master-bin.000001
# canal访问master的binlog，可在master上通过show master status;查看最新一个position
canal.instance.master.position=637
# canal订阅master上的哪些表，以下配置是所有
canal.instance.filter.regex=.*\\..*

# 在canal启动成功并消费binlog成后，会生成一个meta.dat的文件，文件内容如下
# {"clientDatas":[{"clientIdentity":{"clientId":1001,"destination":"example","filter":".*\\..*"},"cursor":{"identity":{"slaveId":-1,"sourceAddress":{"address":"localhost","port":3307}},"postion":{"gtid":"","included":false,"journalName":"master-bin.000001","position":0,"serverId":1,"timestamp":1554814022000}}}],"destination":"example"}
# 在这个文件可以修改position的位置，用来重新订阅binlog

```
- 启动canal
```bash
$ target/canal/bin/startup.sh

# 项目启动后不一定会成功，需要在订阅的mysql服务名称的文件夹中打印log，注意你写了多少个服务，就应该在多少个服务中看日志
$ tail -100f ../logs/example/example.log

# 看到以下提示说明启动成功
c.a.otter.canal.instance.core.AbstractCanalInstance - start successful....

```

- 客户端订阅代码
```$java

public static void main(String args[]) {
        // 创建链接
        CanalConnector connector = CanalConnectors
                .newSingleConnector(new InetSocketAddress("127.0.0.1", 11111), "example", "", "");
        int batchSize = 1000;
        int emptyCount = 0;
        try {
            connector.connect();
            connector.subscribe(".*\\..*");
            connector.rollback();
            int totalEmtryCount = 1200;
            while (emptyCount < totalEmtryCount) {
                Message message = connector.getWithoutAck(batchSize); // 获取指定数量的数据
                long batchId = message.getId();
                int size = message.getEntries().size();
                if (batchId == -1 || size == 0) {
                    emptyCount++;
                    System.out.println("empty count : " + emptyCount);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    emptyCount = 0;
                    // System.out.printf("message[batchId=%s,size=%s] \n",
                    // batchId, size);
                    printEntry(message.getEntries());
                }

                connector.ack(batchId); // 提交确认
                // connector.rollback(batchId); // 处理失败, 回滚数据
            }

            System.out.println("empty too many times, exit");
        } finally {
            connector.disconnect();
        }
    }

    private static void printEntry(List<Entry> entrys) {
        for (Entry entry : entrys) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN
                    || entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue;
            }

            RowChange rowChage = null;
            try {
                rowChage = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("ERROR ## parser of eromanga-event has an error , data:" + entry.toString(),
                        e);
            }

            EventType eventType = rowChage.getEventType();
            System.out.println(String.format("================> binlog[%s:%s] , name[%s,%s] , eventType : %s",
                    entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(),
                    entry.getHeader().getSchemaName(), entry.getHeader().getTableName(), eventType));

            for (RowData rowData : rowChage.getRowDatasList()) {
                if (eventType == EventType.DELETE) {
                    printColumn(rowData.getBeforeColumnsList());
                } else if (eventType == EventType.INSERT) {
                    printColumn(rowData.getAfterColumnsList());
                } else {
                    System.out.println("-------> before");
                    printColumn(rowData.getBeforeColumnsList());
                    System.out.println("-------> after");
                    printColumn(rowData.getAfterColumnsList());
                }
            }
        }
    }

    private static void printColumn(List<Column> columns) {
        for (Column column : columns) {
            System.out.println(column.getName() + " : " + column.getValue() + "    update=" + column.getUpdated());
        }
    }
```

- 测试
在master修改数据，在客户端代码中能够接受到修改操作
## Read More
[谈谈对Canal（ 增量数据订阅与消费 ）的理解](http://www.importnew.com/25189.html)

