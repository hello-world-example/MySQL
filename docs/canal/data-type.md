# 数据格式

## FlatMessage - Message

- com.alibaba.otter.canal.common.MQMessageUtils
  - `messageConverter()` 将Message转换为FlatMessage
- com.alibaba.otter.canal.protocol.FlatMessage
- com.alibaba.otter.canal.protocol.Message



- `List<CanalEntry.Entry> entrys = message.getEntries();`
- `CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());`
- `CanalEntry.EventType eventType = rowChange.getEventType();`
- `for (CanalEntry.RowData rowData : rowChange.getRowDatasList())`
- `List<CanalEntry.Column> columns = rowData.getBeforeColumnsList();`
- `List<CanalEntry.Column> columns = rowData.getAfterColumnsList();`
- 

|                             | FlatMessage | Message                                                      |                               |
| --------------------------- | ----------- | ------------------------------------------------------------ | ----------------------------- |
| `long`                      | `id`        | `message.getId()`                                            |                               |
| `String`                    | `database`  | `entry.getHeader().getSchemaName()`                          | 数据库名                      |
| `String`                    | `table`     | `entry.getHeader().getTableName()`                           | 表名                          |
| `List<String>`              | `pkNames`   | `if (column.getIsKey()) flatMessage.addPkName(column.getName()); ` | 主键名                        |
| `Boolean`                   | `isDdl`     | `rowChange.getIsDdl()`                                       | 是否 DDL 语句                 |
| `String`                    | `type`      | `eventType.toString()`                                       | CanalEntry.EventType 事件类型 |
| `Long`                      | `es`        | `entry.getHeader().getExecuteTime()`                         | binlog executeTime            |
| `Long`                      | `ts`        | `System.currentTimeMillis()`                                 | dml build timeStamp           |
| `String`                    | `sql`       | `rowChange.getSql()`                                         | 执行的SQL语句                 |
| ---------                   | ---------   | ---------                                                    | ---------                     |
| `Map<String, Integer>`      | `sqlType`   | `sqlType.put(column.getName(), column.getSqlType());`        | MySQL 数据类型                |
| `Map<String, String>`       | `mysqlType` | `mysqlType.put(column.getName(), column.getMysqlType());`    | Java 数据类型                 |
| `List<Map<String, String>>` | `data`      | `rowData.getAfterColumnsList()`                              | 新数据                        |
| `List<Map<String, String>>` | `old`       | `rowData.getBeforeColumnsList()`                             | 老数据                        |

## CanalEntry.EntryType

```java
/**
 * <code>TRANSACTIONBEGIN = 1;</code>
 */
public static final int TRANSACTIONBEGIN_VALUE = 1;
/**
 * <code>ROWDATA = 2;</code>
 */
public static final int ROWDATA_VALUE = 2;
/**
 * <code>TRANSACTIONEND = 3;</code>
 */
public static final int TRANSACTIONEND_VALUE = 3;
/**
 * <code>HEARTBEAT = 4;</code>
 *
 * <pre>
 * * 心跳类型，内部使用，外部暂不可见，可忽略 *
 * </pre>
 */
public static final int HEARTBEAT_VALUE = 4;
/**
 * <code>GTIDLOG = 5;</code>
 */
public static final int GTIDLOG_VALUE = 5;
```

## CanalEntry.EventType

```java
/**
 * <code>INSERT = 1;</code>
 */
INSERT(0, 1),
/**
 * <code>UPDATE = 2;</code>
 */
UPDATE(1, 2),
/**
 * <code>DELETE = 3;</code>
 */
DELETE(2, 3),
/**
 * <code>CREATE = 4;</code>
 */
CREATE(3, 4),
/**
 * <code>ALTER = 5;</code>
 */
ALTER(4, 5),
/**
 * <code>ERASE = 6;</code>
 */
ERASE(5, 6),
/**
 * <code>QUERY = 7;</code>
 */
QUERY(6, 7),
/**
 * <code>TRUNCATE = 8;</code>
 */
TRUNCATE(7, 8),
/**
 * <code>RENAME = 9;</code>
 */
RENAME(8, 9),
/**
 * <code>CINDEX = 10;</code>
 *
 * <pre>
 * *CREATE INDEX*
 * </pre>
 */
CINDEX(9, 10),
/**
 * <code>DINDEX = 11;</code>
 */
DINDEX(10, 11),
/**
 * <code>GTID = 12;</code>
 */
GTID(11, 12),
/**
 * <code>XACOMMIT = 13;</code>
 *
 * <pre>
 * * XA *
 * </pre>
 */
XACOMMIT(12, 13),
/**
 * <code>XAROLLBACK = 14;</code>
 */
XAROLLBACK(13, 14),
/**
 * <code>MHEARTBEAT = 15;</code>
 *
 * <pre>
 * * MASTER HEARTBEAT *
 * </pre>
 */
MHEARTBEAT(14, 15),;
```





## JSON

### CREATE TABLE (CREATE)

```json
[
    {
        "data":null,
        "database":"test",
        "es":1554044748000,
        "id":6,
        "isDdl":true,
        "mysqlType":null,
        "old":null,
        "pkNames":null,
        "sql":"CREATE TABLE `test`.`asd_copy` (
`ID` int(11) NOT NULL,
PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8",
        "sqlType":null,
        "table":"asd_copy",
        "ts":1554044748116,
        "type":"CREATE"
    }
]
```

### INSERT

```json
[
    {
        "data":[
            {
                "ID":"2223"
            }
        ],
        "database":"test",
        "es":1554044748000,
        "id":6,
        "isDdl":false,
        "mysqlType":{
            "ID":"int(11)"
        },
        "old":null,
        "pkNames":[
            "ID"
        ],
        "sql":"",
        "sqlType":{
            "ID":4
        },
        "table":"asd_copy",
        "ts":1554044748117,
        "type":"INSERT"
    }
]
```

### UPDATE

```json
[
    {
        "data":[
            {
                "ID":"222"
            }
        ],
        "database":"test",
        "es":1554044876000,
        "id":7,
        "isDdl":false,
        "mysqlType":{
            "ID":"int(11)"
        },
        "old":[
            {
                "ID":"2223"
            }
        ],
        "pkNames":[
            "ID"
        ],
        "sql":"",
        "sqlType":{
            "ID":4
        },
        "table":"asd",
        "ts":1554044877622,
        "type":"UPDATE"
    }
]
```

### DELETE

```json
[
    {
        "data":[
            {
                "ID":"222"
            }
        ],
        "database":"test",
        "es":1554044940000,
        "id":8,
        "isDdl":false,
        "mysqlType":{
            "ID":"int(11)"
        },
        "old":null,
        "pkNames":[
            "ID"
        ],
        "sql":"",
        "sqlType":{
            "ID":4
        },
        "table":"asd",
        "ts":1554044940882,
        "type":"DELETE"
    }
]
```

### ALTER TABLE (CINDEX)
```json
[
    {
        "ts":1554041367827,
        "database":"test",
        "id":5,
        "isDdl":true,
        "sql":"ALTER TABLE `test`.`asd` ADD PRIMARY KEY (`ID`)",
        "table":"asd",
        "type":"CINDEX"
        "es":1554041367000,
    }
]
```

### DROP TABLE (ERASE)

```json
[
    {
        "data":null,
        "database":"test",
        "es":1554045030000,
        "id":9,
        "isDdl":true,
        "mysqlType":null,
        "old":null,
        "pkNames":null,
        "sql":"DROP TABLE `asd` /* generated by server */",
        "sqlType":null,
        "table":"asd",
        "ts":1554045031258,
        "type":"ERASE"
    }
]
```

### ALTER TABLE (RENAME)

```json
[
    {
        "database":"test",
        "es":1554045087000,
        "id":10,
        "isDdl":true,
        "sql":"alter table `asd_copy` rename to `asd`",
        "table":"asd",
        "ts":1554045087466,
        "type":"RENAME"
    }
]
```

### ALTER TABLE (ALTER)

```json
[
    {
        "database":"test",
        "es":1554045155000,
        "id":11,
        "isDdl":true,
        "sql":"ALTER TABLE `test`.`asd` ADD COLUMN `TEST_NAME` varchar(255) AFTER `ID`",
        "table":"asd",
        "ts":1554045155706,
        "type":"ALTER"
    }
]
```



```json
[
    {
        "database":"test",
        "es":1554045230000,
        "id":12,
        "isDdl":true,
        "sql":"ALTER TABLE `test`.`asd` DROP COLUMN `TEST_NAME`",
        "table":"asd",
        "ts":1554045230994,
        "type":"ALTER"
    }
]
```

### 更新多行

```
[
    {
        "data":[
            {
                "ID":"22",
                "TEST_NAME":"123"
            },
            {
                "ID":"2223",
                "TEST_NAME":"123"
            }
        ],
        "database":"test",
        "es":1554045359000,
        "id":15,
        "isDdl":false,
        "mysqlType":{
            "ID":"int(11)",
            "TEST_NAME":"varchar(255)"
        },
        "old":[
            {
                "TEST_NAME":null
            },
            {
                "TEST_NAME":null
            }
        ],
        "pkNames":[
            "ID",
            "ID"
        ],
        "sql":"",
        "sqlType":{
            "ID":4,
            "TEST_NAME":12
        },
        "table":"asd",
        "ts":1554045360514,
        "type":"UPDATE"
    }
]
```

### 删除多行

```json
[
    {
        "data":[
            {
                "ID":"22",
                "TEST_NAME":"123"
            },
            {
                "ID":"2223",
                "TEST_NAME":"123"
            }
        ],
        "database":"test",
        "es":1554045542000,
        "id":16,
        "isDdl":false,
        "mysqlType":{
            "ID":"int(11)",
            "TEST_NAME":"varchar(255)"
        },
        "old":null,
        "pkNames":[
            "ID",
            "ID"
        ],
        "sql":"",
        "sqlType":{
            "ID":4,
            "TEST_NAME":12
        },
        "table":"asd",
        "ts":1554045543209,
        "type":"DELETE"
    }
]
```

