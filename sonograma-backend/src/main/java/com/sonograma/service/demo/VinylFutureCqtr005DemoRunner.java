package com.sonograma.service.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "sonograma.demo.vinylfuture-cqtr005",
    name = "enabled",
    havingValue = "true"
)
public class VinylFutureCqtr005DemoRunner implements ApplicationRunner {

    private final VinylFutureCqtr005DemoSeedService seedService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            VinylFutureCqtr005DemoSeedService.SeedResult result = seedService.seed();
            log.info(
                "VinylFuture CQTR005 demo seed finished created={} discoId={} copyId={} copyNumber={} "
                    + "qrCode={} qrContent='{}' qrDownloadUrl='{}' qrPngBytes={}",
                result.created(),
                result.discoId(),
                result.copyId(),
                result.copyNumber(),
                result.qrCode(),
                result.qrContent(),
                result.qrDownloadUrl(),
                result.qrPngBytes()
            );
        } catch (Exception ex) {
            exitCode = 1;
            log.error("VinylFuture CQTR005 demo seed failed: {}", ex.getMessage(), ex);
        }
        int finalExitCode = exitCode;
        System.exit(SpringApplication.exit(applicationContext, () -> finalExitCode));
    }
}
