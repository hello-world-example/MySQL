package xyz.kail.demo.mysql.oneproxy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.kail.demo.mysql.oneproxy.dao.UserDAO;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
@Service
public class UserService {

    @Resource
    private UserDAO userDAO;

    @Resource
    private ApplicationContext context;

    @Value("${spring.jta.enabled}")
    private Boolean asd;

    @PostConstruct
    public void init() {

    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public void selectOne() {
        for (int i = 0; i < 10; i++) {
            userDAO.selectOne();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOne() {
        for (int i = 0; i < 10; i++) {
            userDAO.selectOne();
        }
    }

}
