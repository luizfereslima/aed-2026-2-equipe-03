package br.pucminas.aed.vendaingressospainel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VendaApplication {

    public static void main(String[] args) {
        SpringApplication.run(VendaApplication.class, args);
    }
}
