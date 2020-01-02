package xyz.kail.demo.mysql.oneproxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import xyz.kail.demo.mysql.oneproxy.service.UserService;

import javax.annotation.Resource;

@SpringBootTest
@RunWith(SpringRunner.class)
public class OneProxyApplicationTest {

    @Resource
    private UserService userService;

    @Test
    public void testOne() {
        userService.selectOne();
    }

}
