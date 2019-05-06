# 消费失败通知方案

1. 当消费失败回滚时，进行通知，代码如下

   ```
   private void rollback(MessageConsumer.ConsumeResult consumeResult, Message message) {

           if (connector != null && message != null) {
               connector.rollback(message.getId());
           }
           stop(consumeResult.getErrorDataDml());
       }
    /**
     * 设置条件停止
     * @author zhida.chen@ttpai.cn
     *
     */
   public void stop(DataDml dataDml) {

   	running = Boolean.FALSE;
   	warning.warning(dataDml);
   }
   ```

2. warning是一个接口，DataDml是通知的数据，通知方案暂时只有邮件通知

3. 邮件通知内容，集成邮件，参考[Spring Boot中使用JavaMailSender发送邮件](http://blog.didispace.com/springbootmailsender/)

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
   1. 程序bug不能解析canal传递过来的sql
      解决方案：解决bug，并重新上线并消费
   2. canal server或者是destination的配置文件配置错误
      解决方案：检查canal server和destination的配置文件，并配置正确
   3. 消费成功，但是提交消费成功时，网络出错，导致postgresql实际已消费成功，但是canal    却消费失败？
   ```