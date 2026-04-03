package io.coincraft.x402;

import io.coincraft.x402.support.Eip3009Properties;
import io.coincraft.x402.support.FacilitatorProperties;
import io.coincraft.x402.support.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({Eip3009Properties.class, FacilitatorProperties.class, SecurityProperties.class})
public class X402PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(X402PaymentServiceApplication.class, args);
    }
}
