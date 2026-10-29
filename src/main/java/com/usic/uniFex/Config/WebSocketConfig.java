package com.usic.uniFex.Config;

import java.security.Principal;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String PREFIJO_PUBLICO = "/topic/publico/";

    private static final List<String> TOPICS_ADMINISTRACION = List.of("/topic/interesados");

    private final JwtService jwtService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] { 10_000, 0 })
                .setTaskScheduler(programadorLatidos());
        config.setApplicationDestinationPrefixes("/app");
    }

    private ThreadPoolTaskScheduler programadorLatidos() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix("ws-latido-");
        s.setDaemon(true);
        s.initialize();
        return s;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HandshakeWebSocketLog())
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return mensaje;
                if (StompCommand.CONNECT.equals(acc.getCommand())) {
                    autenticar(acc);
                } else if (StompCommand.SUBSCRIBE.equals(acc.getCommand())
                        || StompCommand.SEND.equals(acc.getCommand())) {
                    exigirPermiso(acc);
                }
                return mensaje;
            }
        });
    }

    private void autenticar(StompHeaderAccessor acc) {
        String cabecera = acc.getFirstNativeHeader("Authorization");
        if (cabecera == null) {
            return;
        }
        if (!cabecera.startsWith("Bearer ")) {
            throw new MessagingException("Cabecera Authorization mal formada en el CONNECT");
        }
        try {
            JwtUser usuario = jwtService.validar(cabecera.substring(7));
            acc.setUser(new UsernamePasswordAuthenticationToken(
                    usuario, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rolNormalizado()))));
        } catch (Exception e) {
            throw new MessagingException("Token invalido o expirado");
        }
    }

    private void exigirPermiso(StompHeaderAccessor acc) {
        String destino = acc.getDestination();
        boolean esSuscripcion = StompCommand.SUBSCRIBE.equals(acc.getCommand());
        Principal usuario = acc.getUser();
        if (usuario == null) {
            boolean suscripcionPublica = esSuscripcion && destino != null && destino.startsWith(PREFIJO_PUBLICO);
            if (!suscripcionPublica) {
                throw new MessagingException("Destino no permitido sin autenticacion: " + destino);
            }
            return;
        }
        if (esSuscripcion && destino != null
                && TOPICS_ADMINISTRACION.stream().anyMatch(destino::startsWith)
                && !esAdministracion(usuario)) {
            throw new MessagingException("Solo administracion puede suscribirse a " + destino);
        }
    }

    private boolean esAdministracion(Principal usuario) {
        return usuario instanceof Authentication a && a.getAuthorities().stream()
                .anyMatch(g -> Roles.AUTORIDADES_ADMINISTRA.contains(g.getAuthority()));
    }
}
