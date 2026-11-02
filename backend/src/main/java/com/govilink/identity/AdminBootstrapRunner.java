package com.govilink.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService service;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public AdminBootstrapRunner(
            AdminBootstrapService service,
            Environment environment,
            ConfigurableApplicationContext context
    ) {
        this.service = service;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            String webApplicationType = environment.getProperty(
                    "spring.main.web-application-type"
            );

            if (!"none".equalsIgnoreCase(webApplicationType)) {
                throw new IllegalStateException(
                        "Bootstrap must run with "
                                + "--spring.main.web-application-type=none."
                );
            }

            var result = service.createFirstAdmin(
                    environment.getProperty(
                            "GOVILINK_ADMIN_EMAIL"
                    ),
                    environment.getProperty(
                            "GOVILINK_ADMIN_NAME",
                            "GoviLink Administrator"
                    ),
                    environment.getProperty(
                            "GOVILINK_ADMIN_PASSWORD"
                    )
            );

            System.out.println(
                    "First admin created: " + result.email()
            );
        } finally {
            context.close();
        }
    }
}