package com.dididi.booking.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Bat @Scheduled cho integration module (khong can sua DididiBookingPlatformApplication).
 *
 * <p>P1-11: mặc định Spring chạy MỌI @Scheduled trên MỘT thread. Dự án đang có 9 job nền, trong đó
 * có job gọi mạng ra ngoài (đồng bộ PMS, đối soát VNPay, đẩy index Meilisearch). Một job treo là
 * kéo theo tất cả cùng đứng: hết hạn giữ chỗ không chạy (phòng bị giam), ghi sổ ví vendor không
 * chạy (vendor không thấy tiền), watchdog không chạy (sự cố không ai biết) — mà log vẫn im lặng.</p>
 */
@Configuration
@EnableScheduling
public class IntegrationConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);                       // đủ để job chậm không chặn job nhanh
        s.setThreadNamePrefix("dididi-job-");
        s.setWaitForTasksToCompleteOnShutdown(true);
        s.setAwaitTerminationSeconds(20);       // đang ghi sổ tiền thì cho chạy nốt rồi mới tắt
        return s;
    }
}
