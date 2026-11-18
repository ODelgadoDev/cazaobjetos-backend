package mx.utch.cazaobjetos.service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import nl.martijndwars.webpush.Utils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest(properties={"app.push.enabled=true","app.push.worker-enabled=false","app.push.key-file=/tmp/caza-push-unit-keys/vapid.properties"})
@ActiveProfiles("test")
class PushServiceTest {
    static class MutableClock extends Clock {Instant value=Instant.parse("2026-11-18T16:05:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(value,z);}public Instant instant(){return value;}}
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock testClock(){return new MutableClock();}}
    @Autowired MutableClock clock;
    @Autowired PushSubscriptionService subscriptions;
    @Autowired PushDataService data;
    @Autowired PushDeliveryService deliveries;
    @Autowired PushReminderService reminders;
    @Autowired NotificationService notices;
    @Autowired ItemService items;
    @Autowired ClaimDossierService dossier;
    @Autowired JdbcTemplate db;
    @Autowired VapidKeys keys;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PushTransport transport;
    UUID finder,owner,other;
    @BeforeEach void setup()throws Exception {db.execute("TRUNCATE app_user,email_outbox CASCADE");finder=user("finder");owner=user("owner");other=user("other");clock.value=Instant.parse("2026-11-18T16:05:00Z");when(transport.send(any(),any())).thenReturn(new PushTransport.Result(201,0));}
    UUID user(String name){var id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,"STUDENT");return id;}
    PushKeysDto browserKeys(){try{var g=KeyPairGenerator.getInstance("ECDH","BC");g.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));byte[] auth=new byte[16];new SecureRandom().nextBytes(auth);return new PushKeysDto(Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey)g.generateKeyPair().getPublic())),Base64.getUrlEncoder().withoutPadding().encodeToString(auth));}catch(Exception e){throw new RuntimeException(e);}}
    PushSubscriptionRequestDto request(UUID user){return new PushSubscriptionRequestDto(user,"https://fcm.googleapis.com/fcm/send/"+UUID.randomUUID(),browserKeys());}
    PushSubscriptionResponseDto register(UUID user,String session){return subscriptions.register(user,session,request(user));}
    ReportRequestDto report(){return new ReportRequestDto("Objeto","Ficha pública",Category.OTHER,"I",LocalDate.now());}
    UUID found(){return items.createFound(finder,report());}
    long count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    long state(String status){return db.queryForObject("SELECT count(*) FROM push_delivery WHERE status=?",Long.class,status);}
    long kind(String kind){return db.queryForObject("SELECT count(*) FROM push_delivery WHERE kind=?",Long.class,kind);}
    void fails(int status,Runnable f){assertEquals(status,assertThrows(BusinessException.class,f::run).getStatusCode().value());}
    void readAll(UUID actor){for(var n:notices.list(actor,NotificationFilter.ALL,0,100).items())notices.read(actor,n.id());}
    @Test void keysPersistAndConfigContainsOnlyPublicMaterial(){var again=new VapidKeys(true,"/tmp/caza-push-unit-keys/vapid.properties");assertEquals(keys.publicKey(),again.publicKey());assertEquals(65,Base64.getUrlDecoder().decode(keys.publicKey()).length);assertTrue(Utils.verifyKeyPair(again.pair().getPrivate(),again.pair().getPublic()));}
    @Test void registrationIsStableButReactivationRotatesItsBinding(){var request=request(finder);var first=subscriptions.register(finder,"session-a",request);var second=subscriptions.register(finder,"session-b",request);assertEquals(first,second);assertEquals(1,count("push_subscription"));subscriptions.disable(finder,first.id());var third=subscriptions.register(finder,"session-b",request);assertEquals(first.id(),third.id());assertNotEquals(first.bindingToken(),third.bindingToken());}
    @Test void accountTransferRequiresProofAndCancelsOldQueuedPayloads()throws Exception {
        var original=request(finder);var a=subscriptions.register(finder,"session-a",original);found();
        fails(409,()->subscriptions.register(owner,"session-b",original));
        fails(409,()->subscriptions.register(owner,"session-b",new PushSubscriptionRequestDto(owner,original.endpoint(),browserKeys())));
        var b=subscriptions.register(owner,"session-b",new PushSubscriptionRequestDto(owner,original.endpoint(),original.keys()));assertNotEquals(a.bindingToken(),b.bindingToken());deliveries.deliver();assertEquals(1,state("CANCELLED"));verify(transport,never()).send(any(),any());
    }
    @Test void logoutDisablesOnlyThatAccountSessionAndForeignDeviceIsNotAccessible(){var a=register(finder,"a");var b=register(finder,"b");var c=register(owner,"c");subscriptions.logout(finder,"a");assertFalse(data.lock(a.id()).orElseThrow().active());assertTrue(data.lock(b.id()).orElseThrow().active());assertTrue(data.lock(c.id()).orElseThrow().active());fails(404,()->subscriptions.disable(other,b.id()));fails(404,()->subscriptions.test(finder,"a",b.id()));}
    @Test void credentialRevocationCancelsPendingSendsAndCanReactivateSafely()throws Exception {var req=request(finder);var before=subscriptions.register(finder,"a",req);found();db.update("UPDATE app_user SET credentials_version=credentials_version+1 WHERE id=?",finder);deliveries.deliver();assertEquals(1,state("CANCELLED"));verify(transport,never()).send(any(),any());var after=subscriptions.register(finder,"b",req);assertNotEquals(before.bindingToken(),after.bindingToken());}
    @Test void eventPushContainsNoPrivateTextAndDoesNotReadTheInbox()throws Exception {
        register(finder,"a");register(owner,"b");UUID item=found(),claim=items.claim(owner,item,new ClaimRequestDto("SECRETO original",null));dossier.comment(owner,claim,new ClaimCommentRequestDto("SECRETO comentario"),"push-comment-01");items.decide(finder,claim,"accept");items.decide(owner,claim,"complete");
        while(state("PENDING")>0)deliveries.deliver();assertEquals(count("push_delivery"),state("SENT"));var payload=org.mockito.ArgumentCaptor.forClass(byte[].class);verify(transport,atLeastOnce()).send(any(),payload.capture());
        for(byte[] bytes:payload.getAllValues()){String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);assertFalse(text.contains("SECRETO"));assertFalse(text.contains("body"));assertTrue(text.contains("binding"));assertTrue(text.contains("notificationId"));}
        assertEquals(notices.summary(owner).total(),notices.summary(owner).unread());assertEquals(notices.summary(finder).total(),notices.summary(finder).unread());
    }
    @Test void readingAndClearingBeforeDeliveryCancelsPushWithoutDeletingHistory()throws Exception {register(finder,"a");found();readAll(finder);notices.clearRead(finder);long history=count("audit_event");deliveries.deliver();assertEquals(1,state("CANCELLED"));verify(transport,never()).send(any(),any());assertEquals(history,count("audit_event"));assertEquals(1,count("user_notification"));}
    @Test void transientFailuresRespectBackoffAndRetryAfter()throws Exception {
        register(finder,"a");found();when(transport.send(any(),any())).thenReturn(new PushTransport.Result(503,0),new PushTransport.Result(429,120),new PushTransport.Result(201,0));
        deliveries.deliver();assertEquals(1,state("PENDING"));deliveries.deliver();verify(transport,times(1)).send(any(),any());clock.value=clock.value.plusSeconds(31);deliveries.deliver();assertEquals(2,db.queryForObject("SELECT attempts FROM push_delivery",Integer.class));clock.value=clock.value.plusSeconds(119);deliveries.deliver();verify(transport,times(2)).send(any(),any());clock.value=clock.value.plusSeconds(2);deliveries.deliver();assertEquals(1,state("SENT"));assertEquals(3,db.queryForObject("SELECT attempts FROM push_delivery",Integer.class));
    }
    @Test void expiredEndpointDeactivatesDeviceAndPermanentErrorsDoNotLoop()throws Exception {var sub=register(finder,"a");found();when(transport.send(any(),any())).thenReturn(new PushTransport.Result(410,0));deliveries.deliver();assertEquals(1,state("CANCELLED"));assertFalse(data.lock(sub.id()).orElseThrow().active());items.createFound(finder,report());assertEquals(1,count("push_delivery"));register(owner,"b");items.createLost(owner,report());when(transport.send(any(),any())).thenReturn(new PushTransport.Result(400,0));deliveries.deliver();assertEquals(1,state("FAILED"));}
    @Test void retriesStopAtFiveWithoutDeactivatingAValidSubscription()throws Exception {var sub=register(finder,"a");found();when(transport.send(any(),any())).thenReturn(new PushTransport.Result(403,0));for(int i=0;i<5;i++){deliveries.deliver();clock.value=clock.value.plusSeconds(600);}assertEquals(1,state("FAILED"));assertEquals(5,db.queryForObject("SELECT attempts FROM push_delivery",Integer.class));assertTrue(data.lock(sub.id()).orElseThrow().active());}
    @Test void deviceLimitAndPreferenceBoundsAreEnforced(){assertEquals(1,reminders.preference(finder).dailyReminders());for(int i=0;i<10;i++)register(finder,"s"+i);fails(409,()->register(finder,"eleven"));fails(400,()->reminders.preference(finder,-1));fails(400,()->reminders.preference(finder,3));assertEquals(2,reminders.preference(finder,2).dailyReminders());}
    @Test void remindersAreGroupedPerDayAndSlotWithoutCreatingUnreadNotices(){register(finder,"a");found();long feed=count("user_notification");reminders.generate();reminders.generate();assertEquals(1,kind("REMINDER"));clock.value=Instant.parse("2026-11-19T00:05:00Z");reminders.generate();assertEquals(1,kind("REMINDER"));reminders.preference(finder,2);reminders.generate();assertEquals(2,kind("REMINDER"));clock.value=Instant.parse("2026-11-19T16:05:00Z");reminders.generate();assertEquals(3,kind("REMINDER"));assertEquals(feed,count("user_notification"));}
    @Test void noReminderWithoutAReasonAndPendingReminderCancelsWhenWorkIsResolved(){register(owner,"a");reminders.generate();assertEquals(0,kind("REMINDER"));UUID loss=items.createLost(owner,report());readAll(owner);reminders.generate();assertEquals(1,kind("REMINDER"));items.recoverLost(owner,loss);deliveries.deliver();assertEquals(2,state("CANCELLED"));clock.value=clock.value.plusSeconds(86400);reminders.generate();assertEquals(1,kind("REMINDER"));}
    @Test void loweringFrequencyCancelsAnAlreadyQueuedSecondReminder(){register(finder,"a");found();readAll(finder);UUID loss=items.createLost(finder,report());readAll(finder);reminders.preference(finder,2);clock.value=Instant.parse("2026-11-19T00:05:00Z");reminders.generate();assertEquals(1,kind("REMINDER"));reminders.preference(finder,1);deliveries.deliver();assertEquals(1L,db.queryForObject("SELECT count(*) FROM push_delivery WHERE kind='REMINDER' AND status='CANCELLED'",Long.class));}
    @Test void testPushIsScopedToCurrentSessionAndRateLimited(){var sub=register(finder,"a");assertNotNull(subscriptions.test(finder,"a",sub.id()));fails(429,()->subscriptions.test(finder,"a",sub.id()));fails(404,()->subscriptions.test(owner,"b",sub.id()));clock.value=clock.value.plusSeconds(61);assertNotNull(subscriptions.test(finder,"a",sub.id()));assertEquals(0,count("user_notification"));}
    @Test void rollbackAndLateSubscriptionDoNotReplayEvents(){register(finder,"a");assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(s->{found();throw new IllegalStateException("rollback");}));assertEquals(0,count("push_delivery"));items.createFound(owner,report());register(owner,"b");assertEquals(0,count("push_delivery"));}
    @Test void concurrentReminderSchedulersDeduplicateTheSameWindow()throws Exception {register(finder,"a");found();var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{gate.await();reminders.generate();return true;});var b=pool.submit(()->{gate.await();reminders.generate();return true;});gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}assertEquals(1,kind("REMINDER"));assertEquals(1,count("push_reminder_run"));}
    @Test void expiredQueuedMessagesAreCancelled()throws Exception {register(finder,"a");found();clock.value=clock.value.plusSeconds(3601);deliveries.deliver();assertEquals(1,state("CANCELLED"));verify(transport,never()).send(any(),any());}
}
