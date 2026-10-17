package dev.toka.signs;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class UseNoticeTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();

    @Test public void displayShownDoesNotUseChat(){
        var n=new UseNotice(2000);int[] chat={0};
        assertTrue(n.notice(a,0,()->true,()->chat[0]++));
        assertEquals(0,chat[0]);
    }
    @Test public void unavailableDisplayFallsBackToChat(){
        var n=new UseNotice(2000);int[] chat={0};
        n.notice(a,0,()->false,()->chat[0]++);
        assertEquals(1,chat[0]);
    }
    @Test public void throwingDisplayFallsBackToChat(){
        var n=new UseNotice(2000);int[] chat={0};
        n.notice(a,0,()->{throw new IllegalStateException();},()->chat[0]++);
        n.notice(b,0,()->{throw new NoClassDefFoundError();},()->chat[0]++);
        assertEquals(2,chat[0]);
    }
    @Test public void samePlayerIsThrottledForTwoSeconds(){
        var n=new UseNotice(2000);int[] shown={0};
        assertTrue(n.notice(a,0,()->{shown[0]++;return true;},()->{}));
        assertFalse(n.notice(a,1999,()->{shown[0]++;return true;},()->{}));
        assertTrue(n.notice(b,1000,()->{shown[0]++;return true;},()->{}));
        assertTrue(n.notice(a,2000,()->{shown[0]++;return true;},()->{}));
        assertEquals(3,shown[0]);
    }
    @Test public void throttledCallSendsNothing(){
        var n=new UseNotice(2000);int[] chat={0};
        n.notice(a,0,()->false,()->chat[0]++);
        n.notice(a,500,()->false,()->chat[0]++);
        assertEquals(1,chat[0]);
    }
}
