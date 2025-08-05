package com.events.paymentverifsystem.Utilities.Email;

import javax.mail.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class EmailConfiguration {

    private final EmailProperties properties;

    public EmailConfiguration(EmailProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Session mailSession() {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", properties.getProtocol()); // imaps
        props.setProperty("mail.imaps.host", properties.getHost());
        props.setProperty("mail.imaps.port", String.valueOf(properties.getPort()));
        props.setProperty("mail.imaps.connectiontimeout", String.valueOf(properties.getConnectionTimeout()));
        props.setProperty("mail.imaps.timeout", String.valueOf(properties.getTimeout()));
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.setProperty("mail.imaps.partialfetch", "false");
        props.setProperty("mail.imaps.ssl.trust", "*");
        Session session = Session.getInstance(props);
        session.setDebug(false);
        return session;
    }
}
