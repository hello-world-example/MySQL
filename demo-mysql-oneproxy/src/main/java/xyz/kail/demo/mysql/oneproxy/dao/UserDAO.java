package xyz.kail.demo.mysql.oneproxy.dao;

import net.paoding.rose.jade.annotation.DAO;
import net.paoding.rose.jade.annotation.SQL;

import java.util.List;
import java.util.Map;

@DAO
public interface UserDAO {

    /**
     * LIST SQLTEXT
     */

    // @SQL("select /*master*/ * from t_ttp_user limit 1")
    @SQL("select * from t_ttp_user limit 1")
    List<Map> selectOne();

}
