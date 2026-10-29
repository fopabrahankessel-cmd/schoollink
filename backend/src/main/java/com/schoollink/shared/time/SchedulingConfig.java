package com.schoollink.shared.time;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Nightly and periodic jobs ({@code @Scheduled}), e.g. expiring import previews (D19). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {

}
