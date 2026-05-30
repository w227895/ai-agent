package com.ke.deepseektools.controller;

import com.ke.deepseektools.fewshot.FewShotPlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mail")
public class MailController {

    private static final String FLIGHT_CHANGE_MAIL_SCENARIO = "flight-change-mail";

    private static final String SAMPLE_MAIL = """
            Subject: 航班变更通知 MU5101

            尊敬的旅客张三：
            您预订的 2026-06-02 北京首都T2 至 上海虹桥T2 MU5101 航班因航空公司计划调整发生变更。
            原航班：MU5101，起飞时间 2026-06-02 08:00。
            新航班：MU5115，起飞时间 2026-06-02 10:30。
            票号：781-1234567890，PNR：H8K2Q9。
            请协助联系旅客确认是否接受变更。
            """;

    private final FewShotPlatformService fewShotPlatformService;

    public MailController(FewShotPlatformService fewShotPlatformService) {
        this.fewShotPlatformService = fewShotPlatformService;
    }

    @GetMapping("/process")
    public MailProcessResponse processSampleMail() {
        return processMail(new MailProcessRequest(SAMPLE_MAIL));
    }

    @PostMapping("/process")
    public MailProcessResponse processMail(@RequestBody MailProcessRequest request) {
        String emailContent = request.emailContent();
        if (emailContent == null || emailContent.isBlank()) {
            emailContent = SAMPLE_MAIL;
        }

        String result = fewShotPlatformService.run(FLIGHT_CHANGE_MAIL_SCENARIO, emailContent).result();

        return new MailProcessResponse(emailContent, result);
    }

    public record MailProcessRequest(String emailContent) {
    }

    public record MailProcessResponse(String emailContent, String result) {
    }
}
