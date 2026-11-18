package mx.utch.cazaobjetos.service;
public interface PushTransport {
    record Result(int status,long retryAfterSeconds){}
    Result send(PushDataService.Subscription subscription,byte[] payload)throws Exception;
}
