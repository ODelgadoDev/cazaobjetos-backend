package mx.utch.cazaobjetos.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class PushClockConfig { @Bean public Clock pushClock() {return Clock.systemUTC();} }
