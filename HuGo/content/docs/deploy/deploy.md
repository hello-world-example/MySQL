# giraffe部署方案


###同步表结构

- 可以通过数据部门将数据结构和数据进行同步，并获取binglog的文件和position
- 可以通过一下方式自己生成数据结构和数据
    1. 使用[mysql2pgsql](https://hello-world-example.github.io/MySQL/#/rds_dbsync)生成表结构，使用./mysql2pgsql -d命令

    2. 将以下特殊数据类型替换

       | int unsigned       | BIGINT      |
       | ------------------ | ----------- |
       | tinyint unsigned   | INT         |
       | smallint unsigned  | INT         |
       | mediumint unsigned | INT         |
       | bigint unsigned    | DECIMAL     |
       | float unsigned     | DECIMAL     |
       | double unsigned    | DECIMAL     |
       | tinyint            | INT         |
       | mediumint          | INT         |
       | int                | BIGINT      |
       | tinyblob           | TEXT        |
       | tinytext           | TEXT        |
       | blob               | TEXT        |
       | text               | TEXT        |
       | mediumblob         | TEXT        |
       | mediumtext         | TEXT        |
       | longblob           | TEXT        |
       | longtext           | TEXT        |
       | bit                | TEXT        |
       | datetime           | TIMESTAMP   |
       | timestamp          | TIMESTAMP   |
       | float              | DECIMAL     |
       | double             | DECIMAL     |
       | year               | INT         |
       | time               | VARCHAR(50) |

    3. 导出数据，关于数据备份，参考[mysqldump备份时保持数据一致性](https://blog.csdn.net/anzhen0429/article/details/76096141)，数据库可自定义

       ```
       mysqldump -u root -p -P3307 --single-transaction --master-data --flush-log --database xxx > xxx.sql 
       ```

    4. vim xx.sql，找到change master语句中的MASTER_LOG_FILE和MASTER_LOG_POS的值

    5. 部署canal，参考[安装canal](https://hello-world-example.github.io/MySQL/#/binlog/parse-binlog-by-canal)和[canal简介](https://hello-world-example.github.io/MySQL/#/canal/introduce)，配置canal请参考[canal简介](https://hello-world-example.github.io/MySQL/#/canal/introduce)

    6. 修改binlog的位置

       ```
       canal.instance.master.journal.name=步骤4中找到的MASTER_LOG_FILE
       canal.instance.master.position=步骤4中找到的MASTER_LOG_POS
       ```

    7. 启动canal

    8. 修改giraffe-launcher中的application.properties文件

       ```
       # 可配置成数组，下面的信息按照实际的canal server的信息填写
       canal[0].hostname=127.0.0.1
       canal[0].port=11111
       canal[0].username=
       canal[0].password=
       canal[0].subscribe=.*\\..*
       canal[0].destination=example
       canal[0].fetchSize=1200
       ```

    9. 修改giraffe-adater-postgres中database.properties信息

       ```
       # postgres数据库连接
       postgres.jdbc.driver=org.postgresql.Driver
       postgres.jdbc.url=jdbc:postgresql://127.0.0.1:5432/xxx
       postgres.jdbc.username=postgres
       postgres.jdbc.password=123456
       ```

    10. 打包并启动giraffe项目

       ```
       mvn clean package
       java -jar mysql-to-postgres-transfer.jar
       ```
