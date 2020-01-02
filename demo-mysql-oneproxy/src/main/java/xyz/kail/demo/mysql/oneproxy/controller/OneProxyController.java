package xyz.kail.demo.mysql.oneproxy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kail.demo.mysql.oneproxy.service.UserService;

import javax.annotation.Resource;
import java.util.Date;

@RestController
@RequestMapping("/oneproxy")
public class OneProxyController {

    @Resource
    private UserService userService;

    @GetMapping("/select")
    public Date select() {
        userService.selectOne();
        return new Date();
    }

    @GetMapping("/update")
    public Date update() {
        userService.updateOne();
        return new Date();
    }

}
