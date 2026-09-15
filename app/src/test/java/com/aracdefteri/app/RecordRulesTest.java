package com.aracdefteri.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class RecordRulesTest {
    @Test public void rejectsImpossibleDates(){assertNull(RecordRules.date("31.02.2026"));assertNotNull(RecordRules.date("29.02.2024"));}
    @Test public void paidTaxDoesNotRemainUpcoming(){AppDatabase.Record r=new AppDatabase.Record();r.type="Vergi";r.status="Ödendi";assertFalse(RecordRules.pending(r));}
    private AppDatabase.Record fuel(int km,double q,double cost,String status,String type){AppDatabase.Record r=new AppDatabase.Record();r.type="Yakıt";r.subtype=type;r.km=km;r.quantity=q;r.cost=cost;r.status=status;return r;}
    @Test public void includesPartialFillsBetweenFullTanksButNotBaseline(){
        List<RecordRules.Consumption> result=RecordRules.consumption(Arrays.asList(fuel(1000,50,2500,"full","Benzin"),fuel(1200,10,500,"partial","Benzin"),fuel(1500,20,1000,"full","Benzin")));
        assertEquals(1,result.size());assertEquals(6,result.get(0).per100(),0.001);assertEquals(3,result.get(0).perKm(),0.001);
    }
    @Test public void refusesIncompleteOrMixedFuelIntervals(){
        assertTrue(RecordRules.consumption(Arrays.asList(fuel(1000,50,2500,"full","Benzin"),fuel(1500,30,1500,"full","LPG"))).isEmpty());
        assertTrue(RecordRules.consumption(Arrays.asList(fuel(1000,50,2500,"partial","Benzin"),fuel(1500,30,1500,"full","Benzin"))).isEmpty());
    }
}
