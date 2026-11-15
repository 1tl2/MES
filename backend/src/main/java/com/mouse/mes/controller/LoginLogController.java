package com.mouse.mes.controller;

import com.mouse.mes.entity.LoginLog;
import com.mouse.mes.service.LoginLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.mouse.mes.util.LogQuerySupport;

@RestController
@RequestMapping("/api/login-logs")
public class LoginLogController {

    @Autowired
    private LoginLogService loginLogService;

    @GetMapping
    public List<LoginLog> getAllLoginLogs(@RequestParam(required=false) Long userId,
        @RequestParam(required=false) String username, @RequestParam(required=false) String userCode,
        @RequestParam(required=false) Integer loginType,
        @RequestParam(required=false) String startTime, @RequestParam(required=false) String endTime) {
        java.util.Date start=LogQuerySupport.bound(startTime,false), end=LogQuerySupport.bound(endTime,true);
        LogQuerySupport.validate(start,end);
        return loginLogService.findAll().stream().filter(x -> (userId==null || userId.equals(x.getUserId()))
          && LogQuerySupport.text(x.getUsername(),username) && LogQuerySupport.text(x.getUserCode(),userCode)
          && (loginType==null || loginType.equals(x.getLoginType())) && LogQuerySupport.time(x.getLoginTime(),start,end,endTime!=null && endTime.trim().length()==10))
          .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/{id}")
    public LoginLog getLoginLogById(@PathVariable Long id) {
        return loginLogService.findById(id);
    }

    @GetMapping("/user/{userId}")
    public List<LoginLog> getLoginLogsByUserId(@PathVariable Long userId) {
        return loginLogService.findByUserId(userId);
    }

    @GetMapping("/time-range")
    public List<LoginLog> getLoginLogsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        return getAllLoginLogs(null,null,null,null,startTime,endTime);
    }
}