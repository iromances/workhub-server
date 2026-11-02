
import java.util.*;
import java.time.*;
import java.io.*;
import java.util.stream.*;
import com.lease.saps.model.dto.replacebattery.*;
import com.lease.saps.model.entity.*;
import com.lease.saps.model.entity.replacebattery.*;
import com.lease.saps.model.enums.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

// Four select statements copied unchanged from b83576e6; only mapper namespace is isolated.
public class ReviewQueries {
 static JdbcTemplate db;static SqlSessionTemplate sql;static DeviceFlowSyncPageReq req;
 public static Integer findInSet(String value,String csv){if(value==null||csv==null)return null;String[] xs=csv.split(",",-1);for(int i=0;i<xs.length;i++)if(value.equals(xs[i]))return i+1;return 0;}
 static void expect(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void order(int id,String no,String parent,int type,int subtype,int status,int share,String created){db.update("INSERT INTO customer_order(id,order_no,source_order_no,transaction_type,sub_type,status,share_status,create_time,is_delete,is_cancel,project_no) VALUES(?,?,?,?,?,?,?,?,0,0,'PRJ000060')",id,no,parent,type,subtype,status,share,created);}
 static void flow(int id,String no,int state,int trade,String created){db.update("INSERT INTO replace_battery_device_flow(id,order_no,flow_status,trade_status,create_time,is_delete,is_cancel,version) VALUES(?,?,?,?,?,0,0,1)",id,no,state,trade,created);}
 static List<CustomerOrderEntity> refunds(){return sql.selectList("ReviewQueries.selectRefundOrderSyncPage",req);}
 public static void main(String[] args)throws Exception{
  JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:queries;MODE=MySQL;DB_CLOSE_DELAY=-1");db=new JdbcTemplate(ds);
  db.execute("CREATE ALIAS IF NOT EXISTS FIND_IN_SET FOR \"ReviewQueries.findInSet\"");
  db.execute("CREATE TABLE customer_order(id BIGINT,order_no VARCHAR(64),source_order_no VARCHAR(64),transaction_type INT,sub_type INT,status INT,share_status INT,create_time TIMESTAMP,is_delete INT,is_cancel INT,project_no VARCHAR(64),cserial_no VARCHAR(64))");
  db.execute("CREATE TABLE replace_battery_device_flow(id BIGINT,order_no VARCHAR(64),flow_status INT,trade_status INT,create_time TIMESTAMP,is_delete INT,is_cancel INT,version INT,refund_order_no VARCHAR(1000))");
  db.execute("CREATE TABLE customer_order_asinfo(order_no VARCHAR(64),is_delete INT,payment_status INT)");
  MybatisConfiguration cfg=new MybatisConfiguration();cfg.setMapUnderscoreToCamelCase(true);cfg.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
  try(InputStream in=new FileInputStream(args[0])){new XMLMapperBuilder(in,cfg,args[0],cfg.getSqlFragments()).parse();}
  MybatisSqlSessionFactoryBean bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(cfg);sql=new SqlSessionTemplate(bean.getObject());
  req=new DeviceFlowSyncPageReq();req.setProjectNo("PRJ000060");req.setStartId(0L);req.setPageSize(20);req.setDayStart(LocalDateTime.parse("2026-10-29T00:00:00"));req.setDayEnd(LocalDateTime.parse("2026-10-29T23:59:59"));
  String day="2026-10-29 09:00:00";
  order(1,"P1",null,2,2200,4,4,day);flow(1,"P1",40,4,day);
  order(2,"Pold",null,2,2200,4,4,"2026-10-01 09:00:00");flow(2,"Pold",40,4,"2026-10-01 09:00:00");
  order(3,"Pnew",null,2,2200,4,4,day);
  for(int i=1;i<=6;i++)order(10+i,"R"+i,"P1",3,3001,i==2?2:i==3?3:4,4,day);
  order(17,"Rold","Pold",3,3001,4,4,day);
  db.update("UPDATE customer_order SET is_delete=1 WHERE order_no='R4'");db.update("UPDATE customer_order SET is_cancel=1 WHERE order_no='R5'");db.update("UPDATE replace_battery_device_flow SET refund_order_no='R6' WHERE id=1");
  List<CustomerOrderEntity> created=sql.selectList("ReviewQueries.selectUnCreatedFlowOrderPage",req);expect(created.size()==1&&"Pnew".equals(created.get(0).getOrderNo()),"create filter error");System.out.println("PASS creation query excludes existing flows and refund orders");
  List<CustomerOrderEntity> rs=refunds();expect(rs.size()==1&&"R1".equals(rs.get(0).getOrderNo()),"refund filter error");System.out.println("PASS refund query excludes pending/failed/deleted/cancelled/already recorded; result R1");
  req.setOrderNos(Arrays.asList("Pold"));expect(refunds().isEmpty(),"old payment not excluded");System.out.println("REPRO yesterday refund on Oct01 payment excluded even with manual orders=[Pold]");
  req.setOrderNos(Arrays.asList("P1"));expect(refunds().size()==1,"manual filter error");req.setOrderNos(null);
  for(int i=4;i<=9;i++){order(i,"P"+i,null,2,2200,i==6?3:4,i==9?2:4,day);flow(i,"P"+i,i==4?10:30,i==5?3:i==6?3:4,day);}
  for(int i=7;i<=9;i++)db.update("INSERT INTO customer_order_asinfo VALUES(?,0,4)","P"+i);db.update("INSERT INTO customer_order_asinfo VALUES('P8',0,2)");
  List<DeviceFlowPaySyncDto> ps=sql.selectList("ReviewQueries.selectPaySyncPage",req);expect(ps.stream().map(DeviceFlowPaySyncDto::getId).collect(Collectors.toList()).equals(Arrays.asList(4L,5L,6L)),"pay filter error");System.out.println("PASS pay query / DTO mapping: lagging, status mismatch and failed-no-serial selected");
  List<ReplaceBatteryDeviceFlow> ss=sql.selectList("ReviewQueries.selectSharingSyncPage",req);expect(ss.size()==1&&"P7".equals(ss.get(0).getOrderNo())&&ss.get(0).getFlowStatus()==DeviceFlowProcessProgressEnum.PAY_SUCCESS,"sharing filter or enum mapping error");System.out.println("PASS sharing query / enum mapping: only all-success + parent share-success selected");
  req.setOrderNos(Arrays.asList("P1"));req.setPageSize(1);expect(refunds().get(0).getId()==11,"page first");req.setStartId(11L);expect(refunds().isEmpty(),"cursor skip");System.out.println("PASS ordered id cursor and pageSize bindings");
 }
}
