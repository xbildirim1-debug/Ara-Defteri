package com.aracdefteri.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class OdometerParserTest {
    @Test public void readsTotalMileage(){assertEquals(Arrays.asList(68420),OdometerParser.candidates("ODO 68.420 km\nTRIP 231.4\n80 km/h"));}
    @Test public void noDigitsMeansManualFallback(){assertTrue(OdometerParser.candidates("bulanık gösterge").isEmpty());}
    @Test public void ambiguousResultsRemainSeparate(){assertEquals(Arrays.asList(68420,12345),OdometerParser.candidates("68420\n12345"));}
    @Test public void rejectsSpeedTimeAndTrip(){assertTrue(OdometerParser.candidates("TRIP 12345\n1200 km/h\n12:3456\n12345 miles").isEmpty());}
    @Test public void doesNotTreatDecimalTripAsTotal(){assertTrue(OdometerParser.candidates("1234.5").isEmpty());}
}
