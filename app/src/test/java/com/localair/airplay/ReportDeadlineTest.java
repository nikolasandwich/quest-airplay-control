package com.localair.airplay;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReportDeadlineTest {
    @Test public void ordinaryReportExpiresOnceAndLateAckCannotCompleteIt(){
        ReportDeadline d=new ReportDeadline(100);
        assertFalse(d.expire(1099));assertTrue(d.expire(1100));
        assertFalse(d.acknowledge());assertFalse(d.expire(2100));
        ReportDeadline replacement=new ReportDeadline(2100);
        assertFalse(d.acknowledge());assertTrue(replacement.acknowledge());
    }
    @Test public void timelyAckCancelsFailure(){ReportDeadline d=new ReportDeadline(0);assertTrue(d.acknowledge());assertFalse(d.expire(3000));}
}
