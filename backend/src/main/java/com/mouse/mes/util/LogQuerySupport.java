package com.mouse.mes.util;
import java.time.*;
import java.util.Date;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
public final class LogQuerySupport {
 private LogQuerySupport() {}
 public static Date bound(String value, boolean end) {
  if(value==null || value.isBlank())return null;
  try {
   String s=value.trim();
   LocalDateTime t=s.length()==10 ? (end?LocalDate.parse(s).plusDays(1).atStartOfDay():LocalDate.parse(s).atStartOfDay()) : LocalDateTime.parse(s.replace(' ','T'));
   return Date.from(t.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
  } catch (RuntimeException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"日期格式错误");}
 }
 public static boolean time(Date value, Date start, Date end, boolean exclusive) {
  return (start==null || value!=null && !value.before(start)) && (end==null || value!=null && (exclusive?value.before(end):!value.after(end)));
 }
 public static void validate(Date start, Date end) {if(start!=null && end!=null && start.after(end))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"起始日期晚于结束日期");}
 public static boolean text(String value,String query){return query==null || query.isBlank() || value!=null && value.contains(query);}
}
