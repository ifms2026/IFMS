package com.mkwang.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Workaround for environments where Tomcat's default NIO connector
 * (Http11NioProtocol) fails to start because {@code java.nio.channels.Pipe.open()}
 * cannot establish its internal loopback connection
 * ("Unable to establish loopback connection" / WSAEINVAL on connect()).
 * <p>
 * This has been observed on some Windows machines (JDK 21+/24, certain
 * VPN/antivirus/WSL vEthernet adapters interfering with the Winsock loopback
 * fast path) and blocks the embedded server from starting at all.
 * <p>
 * NIO2 (Http11Nio2Protocol) uses Java's asynchronous channel API
 * (backed by Windows IOCP) instead of a Selector + wakeup Pipe, which avoids
 * this specific failure mode.
 */
@Configuration
public class TomcatProtocolConfig {

    @Value("${app.tomcat.protocol:org.apache.coyote.http11.Http11Nio2Protocol}")
    private String protocol;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProtocolCustomizer() {
        return factory -> factory.setProtocol(protocol);
    }
}
