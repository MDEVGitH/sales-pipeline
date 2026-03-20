package com.crm.qualifier;

import com.crm.qualifier.adapter.inbound.cli.CliAdapter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {

    private final CliAdapter cliAdapter;

    public Application(CliAdapter cliAdapter) {
        this.cliAdapter = cliAdapter;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        cliAdapter.run(args);
    }
}
