package com.aracdefteri.app;
import java.util.*;
public final class ObdProtocolTest {
 static int count;static void check(boolean v){count++;if(!v)throw new AssertionError("Check "+count);}
 static ObdProtocol.Metric metric(int pid){for(var m:ObdProtocol.METRICS)if(m.pid==pid)return m;throw new AssertionError();}
 public static void main(String[]args){
  check(ObdProtocol.value("41 0C 1A F8\r>",metric(12))==1726.0);
  check(ObdProtocol.value("41 05 7B",metric(5))==83.0);
  check(ObdProtocol.value("NO DATA",metric(12))==null);
  check(ObdProtocol.value("41 0D 32",metric(12))==null);
  check(ObdProtocol.value("41 0C 1A",metric(12))==null);
  check(ObdProtocol.value("41 0D 00",metric(13))==0.0);
  check(ObdProtocol.value("41 0D 00\r41 0D 32",metric(13))==null);
  check(ObdProtocol.dtcs("43 01 33 00 00 00 00",0x43).equals(Set.of("P0133")));
  check(ObdProtocol.dtcs("43 03 00 05 21",0x43).equals(Set.of("P0300","P0521")));
  check(ObdProtocol.dtcs("43 02 03 00 05 21",0x43).equals(Set.of("P0300","P0521")));
  check(ObdProtocol.dtcs("43 00 00 00 00 00 00",0x43).isEmpty());
  check(ObdProtocol.dtcs("NO DATA",0x43).isEmpty());
  check(ObdProtocol.dtcs("007\r0:43 03 00 05 21 01\r1:33",0x43).equals(Set.of("P0300","P0521","P0133")));
  check(ObdProtocol.supported("41 00 80 00 00 01",0).equals(Set.of(1,32)));
  check(!ObdProtocol.allowed("04"));check(!ObdProtocol.allowed("ATSH7E0"));check(!ObdProtocol.allowed("2E1234"));
  check(ObdProtocol.describe("P1300").contains("doğrulanamadı"));
  check(ObdProtocol.value("41 A6 00 31 12 BA",metric(0xA6))==321605.8);
  System.out.println("PASS "+count+" OBD protocol checks");
 }
}
