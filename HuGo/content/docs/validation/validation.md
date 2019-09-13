# 数据校验

### 数据库校验

1. mysql查询出所有的数据库(select * from information_schema.SCHEMATA where schema_name not in (information_schema,mysql,performance_schema,sys))，postgresql查询出所有的schema(select * from information_schema.schemata)
2. 查询mysql数据库中的所有表(select * from information_schema.TABLES where table_schema=xxx)，查询postgres下所有schema的所有表数据(select * from pg_tables)
3. 查询出mysql的表结构数据和postgres的表结构数据
4. 对比表结构数据，对于不一致的数据发送邮件通知，集成邮件，参考[Spring Boot中使用JavaMailSender发送邮件](http://blog.didispace.com/springbootmailsender/)

### 数据校验

- 数据校验在于数据的生产者和消费者速度一致才能进行有效校验
- 数据校验的task是每60s运行一次


- 全量数据
  1. 校验表数据量的大小，如果差别不大(由于查询时，mysql可能会插入数据，以及网络延迟，所以只能看一个大概)
- 增量数据校验
  1. 取当前时间-10s<修改时间<当前时间-70s的时间段
  2. 将分别从mysql和postgres中取得数据，转化成对象
  3. 对两个list中的数据进行比较
  4. 将两者不同的数据，通过邮件进行通知，集成邮件，参考[Spring Boot中使用JavaMailSender发送邮件](http://blog.didispace.com/springbootmailsender/)



> - [mysql系统简介](https://blog.csdn.net/xlxxcc/article/details/51754524)

> - [postgres系统表简介](http://www.zhongweicheng.com/?p=3040)

