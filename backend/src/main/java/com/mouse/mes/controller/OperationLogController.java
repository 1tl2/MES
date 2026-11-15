package com.mouse.mes.controller;

import com.mouse.mes.entity.OperationLog;
import com.mouse.mes.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.mouse.mes.util.LogQuerySupport;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping
    public List<OperationLog> getAllOperationLogs(@RequestParam(required=false) Long userId,
        @RequestParam(required=false) String username, @RequestParam(required=false) String userCode,
        @RequestParam(required=false) String module, @RequestParam(required=false) String operation,
        @RequestParam(required=false) String startTime, @RequestParam(required=false) String endTime) {
        java.util.Date start=LogQuerySupport.bound(startTime,false), end=LogQuerySupport.bound(endTime,true);
        LogQuerySupport.validate(start,end);
        return operationLogService.findAll().stream().filter(x -> (userId==null || userId.equals(x.getUserId()))
          && LogQuerySupport.text(x.getUsername(),username) && LogQuerySupport.text(x.getUserCode(),userCode)
          && LogQuerySupport.text(x.getModule(),module) && LogQuerySupport.text(x.getOperation(),operation) && LogQuerySupport.time(x.getOperateTime(),start,end,endTime!=null && endTime.trim().length()==10))
          .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/{id}")
    public OperationLog getOperationLogById(@PathVariable Long id) {
        return operationLogService.findById(id);
    }

    @GetMapping("/user/{userId}")
    public List<OperationLog> getOperationLogsByUserId(@PathVariable Long userId) {
        return operationLogService.findByUserId(userId);
    }

    @GetMapping("/module/{module}")
    public List<OperationLog> getOperationLogsByModule(@PathVariable String module) {
        return operationLogService.findByModule(module);
    }

    @GetMapping("/time-range")
    public List<OperationLog> getOperationLogsByTimeRange(@RequestParam String startTime, @RequestParam String endTime) {
        return getAllOperationLogs(null,null,null,null,null,startTime,endTime);
    }
}