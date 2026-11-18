package mx.utch.cazaobjetos.service;
import java.security.*;
import nl.martijndwars.webpush.*;
public final class PushCodec extends AbstractPushService<PushCodec> {
    public PushCodec(KeyPair pair,String subject){super(pair,subject);}
    public nl.martijndwars.webpush.HttpRequest encode(Notification notification)throws Exception{return prepareRequest(notification,Encoding.AES128GCM);}
}
