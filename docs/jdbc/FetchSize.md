# FetchSize 处理大结果集

在数据导出的时候，MySQL 驱动 默认的 `ResultSet` 实现，会一次性把所有的结果加载到内存中，如果查询结果集特别大， 可能会 OOM，常见的避免方式有：

- **limit 分页导出**，但是这种方式**越到后面，效率越低**，因为每次导出都要从 0 开始，跳过所有已导出的数据
- **根据主键从上次导出的最后一个位置开始导出**，如：`SELECT * FROM TABLE WHERE PK > xxx LIMIT x`，但是这种方式在**联合主键的情况下可能会有问题**，操作起来比较麻烦

这里主要介绍，使用 `FetchSize` 的方式进行导出，每次拉取指定量的数据进行处理，避免 OOM，直到所有数据拉取完毕。

> 注意：直接设置 `statement.setFetchSize(100)` 是不起作用的，还是会出现 OOM



## 如何设置

### 正确使用 FetchSize（推荐）

```java
final Statement statement = connection.createStatement(
        ResultSet.TYPE_FORWARD_ONLY,
        ResultSet.CONCUR_READ_ONLY
);
// 必须是 Integer.MIN_VALUE
statement.setFetchSize(Integer.MIN_VALUE);
```

或者 

```java
statement = connection.prepareStatement(sql);
// 必须是 Integer.MIN_VALUE
statement.setFetchSize(Integer.MIN_VALUE);
```


> ```java
> // @see com.mysql.jdbc.ConnectionImpl
> public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException {
>   return prepareStatement(sql, DEFAULT_RESULT_SET_TYPE, DEFAULT_RESULT_SET_CONCURRENCY);
> }
> ```
>
> - `com.mysql.jdbc.ConnectionImpl`
>   - `int DEFAULT_RESULT_SET_TYPE = ResultSet.TYPE_FORWARD_ONLY`
>   - `int DEFAULT_RESULT_SET_CONCURRENCY = ResultSet.CONCUR_READ_ONLY`	



### 使用 MySQL 驱动原生方法

```java
statement.unwrap(com.mysql.jdbc.Statement.class).enableStreamingResults();
```

通过源码的可以看出，实际上跟第一种方式是一样

```java
// @see com.mysql.jdbc.StatementImpl
public void enableStreamingResults() throws SQLException {
    synchronized (checkClosed().getConnectionMutex()) {
        this.originalResultSetType = this.resultSetType;
        this.originalFetchSize = this.fetchSize;

        setFetchSize(Integer.MIN_VALUE); // ❤
        setResultSetType(ResultSet.TYPE_FORWARD_ONLY); // ❤
    }
}
```



### 设置  JDBC 参数

```java
...&useCursorFetch=true
```

且 `Statement 以 TYPE_FORWARD_ONLY` 打开，再设置 `fetchSize(100)`，表示采用服务器端游标，每次从服务器取 `fetchSize` 条数据，**注意这种方式会大量占用服务器的临时空间，一般比较少用**



## 如何使用

使用方式与正常方式没有区别，但是要注意：**不要把所有结果集放入内存集合，否则还是会 OOM**，应该获取一部分，处理一部分，**保证处理过的数据可被 JVM 回收**

```java
// 设置 FetchSize
statement = connection.prepareStatement(sql);
statement.setFetchSize(Integer.MIN_VALUE);

// 使用 StreamingResult
ResultSet resultSet = statement.executeQuery();
// 每次获取部分数据，直到所有数据处理完才会停止，所有的操作由 JDBC 驱动实现
while (resultSet.next()) {
    // do something       
}
```



## ResultSet 配置

> [JDK8 ResultSet Doc](https://docs.oracle.com/javase/8/docs/api/java/sql/ResultSet.html)
>
> [JDK8 ResultSet Doc 中文](http://www.matools.com/file/manual/jdk_api_1.8_google/index.html?overview-summary.html)

| ResultSet 配置             | 分类        | 描述                                        |
| -------------------------- | ----------- | ------------------------------------------- |
| `TYPE_FORWARD_ONLY`        | Type        | 【默认】只能向前移动的 ResultSet 对象的类型 |
| `TYPE_SCROLL_INSENSITIVE`  | Type        | 可滚动，不受 ResultSet 底层数据更改影响     |
| `TYPE_SCROLL_SENSITIVE`    | Type        | 可滚动，受 ResultSet 底层数据更改影响       |
| `CONCUR_READ_ONLY`         | Concurrency | 【默认】只读模式                            |
| `CONCUR_UPDATABLE`         | Concurrency | 标示可以更新                                |
| `CLOSE_CURSORS_AT_COMMIT`  | Holdability | 当提交当前事务时，关闭游标                  |
| `HOLD_CURSORS_OVER_COMMIT` | Holdability | 当提交当前事务时，保持游标                  |


## Read More

- [正确使用MySQL JDBC setFetchSize()方法解决JDBC处理大结果集](https://blog.csdn.net/seven_3306/article/details/9303879)
- [关于oracle与mysql官方jdbc的一些区别](https://blog.csdn.net/yzsind/article/details/7853029)
- [Java不写文件，LOAD DATA LOCAL INFILE大批量导入数据到MySQL的实现](https://blog.csdn.net/seven_3306/article/details/9237495)