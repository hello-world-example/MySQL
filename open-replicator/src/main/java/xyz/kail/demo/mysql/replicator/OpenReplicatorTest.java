package xyz.kail.demo.mysql.replicator;

/**
 * MySQL binlog分析程序测试
 *
 * @author <a href="mailto:573511675@qq.com">menergy</a>
 * DateTime: 13-12-26 下午2:26
 */
public class OpenReplicatorTest {

    public static void main(String[] args) {
        // 配置从MySQL Master进行复制
        final AutoOpenReplicator aor = new AutoOpenReplicator();
        aor.setServerId(1);
        aor.setHost("localhost");
        aor.setUser("root");
        aor.setPassword("1723");
        aor.setAutoReconnect(true);
        aor.setDelayReconnect(5);
        aor.setBinlogEventListener(new NotificationListener());
        aor.start();
    }
}
