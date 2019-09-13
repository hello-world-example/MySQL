# adater模块简介

## 功能简介
adater模块是canal模块实现的由mysql的binlog转储其他第三方的模块，其中包含es，hbase，rdb

## rdb介绍
1. 进入client-adater模块中，进入rdb模块，进入test包，进入sync包下的OracleSyncTest的类中
2. 看到有RdbAdapter的属性，它的主要功能就是将订阅到的数据转储到其他类型的数据库中，在本次案例中是oracle
3. RdbAdapter实现OuterAdapter，OuterAdapter是数据转换的通用接口，输入的参数是Dml类的对象，包含本次转换需要的信息
4. 在前面的例子中，我们知道接受到的数据是Message类的对象，这是在launcher模块中进行了转化

## adater.launcher模块介绍
1. 打开AbstractCanalAdapterWorker类，在writeOut方法中能够看到使用MessageUtil.parse4Dml方法将message转换成Dml对象


## Read More
[谈谈对Canal（ 增量数据订阅与消费 ）的理解](https://github.com/alibaba/canal/wiki/Client-Adapter)