package mx.utch.cazaobjetos.service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import nl.martijndwars.webpush.*;
import org.junit.jupiter.api.Test;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import static org.junit.jupiter.api.Assertions.*;
class PushCryptoTest {
    byte[] hmac(byte[] key,byte[] data)throws Exception{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(data);}
    byte[] cat(byte[]... parts)throws Exception{var out=new java.io.ByteArrayOutputStream();for(var part:parts)out.write(part);return out.toByteArray();}
    byte[] text(String value){return value.getBytes(StandardCharsets.UTF_8);}
    @Test void payloadDecryptsWithReceiverKeyAndVapidSignatureIsValid()throws Exception {
        var vapid=new VapidKeys(true,"/tmp/caza-push-crypto-keys/vapid.properties");var generator=KeyPairGenerator.getInstance("ECDH","BC");generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));var receiver=generator.generateKeyPair();byte[] auth=new byte[16];new SecureRandom().nextBytes(auth);byte[] publicKey=Utils.encode((ECPublicKey)receiver.getPublic());byte[] plain=text("{\"binding\":\"opaque\",\"kind\":\"TEST\"}");
        var notification=new Notification("https://fcm.googleapis.com/fcm/send/test",Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey),Base64.getUrlEncoder().withoutPadding().encodeToString(auth),plain,300);
        var request=new PushCodec(vapid.pair(),"https://utch.edu.mx").encode(notification);assertEquals("aes128gcm",request.getHeaders().get("Content-Encoding"));assertFalse(new String(request.getBody(),StandardCharsets.ISO_8859_1).contains("binding"));
        String jwt=request.getHeaders().get("Authorization").split("t=",2)[1].split(",",2)[0];var claims=new JwtConsumerBuilder().setRequireExpirationTime().setExpectedAudience("https://fcm.googleapis.com").setExpectedSubject("https://utch.edu.mx").setVerificationKey(vapid.pair().getPublic()).build().processToClaims(jwt);assertEquals("https://utch.edu.mx",claims.getSubject());
        // Independent receiver derivation following RFC 8291 section 3.4.
        byte[] body=request.getBody(),salt=Arrays.copyOfRange(body,0,16);int keyLength=Byte.toUnsignedInt(body[20]);assertEquals(65,keyLength);byte[] sender=Arrays.copyOfRange(body,21,21+keyLength);
        var agreement=KeyAgreement.getInstance("ECDH","BC");agreement.init(receiver.getPrivate());agreement.doPhase(Utils.loadPublicKey(sender),true);byte[] prkKey=hmac(auth,agreement.generateSecret());byte[] ikm=hmac(prkKey,cat(text("WebPush: info\0"),publicKey,sender,new byte[]{1}));byte[] prk=hmac(salt,ikm);byte[] cek=Arrays.copyOf(hmac(prk,cat(text("Content-Encoding: aes128gcm\0"),new byte[]{1})),16);byte[] nonce=Arrays.copyOf(hmac(prk,cat(text("Content-Encoding: nonce\0"),new byte[]{1})),12);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(cek,"AES"),new GCMParameterSpec(128,nonce));byte[] decoded=cipher.doFinal(Arrays.copyOfRange(body,21+keyLength,body.length));assertEquals(2,decoded[decoded.length-1]);assertArrayEquals(plain,Arrays.copyOf(decoded,decoded.length-1));
    }
}
