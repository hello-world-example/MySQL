# 消费失败通知方案

1. 当消费失败回滚时，给用户发送邮件，内容如下


```
拉取的canal server的ip
拉取的canal server的端口
拉取的canal server的用户
拉取的canal server的密码
拉取的canal server的destination
拉取的batchId
拉取失败的sql
拉取失败的时间
```

2. 有以下原因会导致消费失败

   ```
   1. 程序bug导致的消费失败
      解决方案：解决bug，并重新上线并消费
   2. canal server或者是destination的配置文件配置错误
      解决方案：检查canal server和destination的配置文件，并配置正确
   ```

   ​