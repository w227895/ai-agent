package com.ke.deepseektools.controller;

import java.util.List;

import com.ke.deepseektools.fewshot.FewShotExample;
import com.ke.deepseektools.fewshot.FewShotPlatformService;
import com.ke.deepseektools.fewshot.FewShotPlatformService.FewShotRunResult;
import com.ke.deepseektools.fewshot.FewShotScenario;
import com.ke.deepseektools.fewshot.FewShotScenarioRepository.FewShotScenarioNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/few-shot")
public class FewShotController {

    private final FewShotPlatformService fewShotPlatformService;

    public FewShotController(FewShotPlatformService fewShotPlatformService) {
        this.fewShotPlatformService = fewShotPlatformService;
    }

    @GetMapping("/scenarios")
    public List<FewShotScenario> listScenarios() {
        return fewShotPlatformService.listScenarios();
    }

    @GetMapping("/scenarios/{code}")
    public FewShotScenario getScenario(@PathVariable String code) {
        return fewShotPlatformService.getScenario(code);
    }

    @PostMapping("/scenarios")
    public FewShotScenario saveScenario(@RequestBody FewShotScenario scenario) {
        return fewShotPlatformService.saveScenario(scenario);
    }

    @PostMapping("/scenarios/{code}/examples")
    public FewShotScenario addExample(@PathVariable String code, @RequestBody FewShotExample example) {
        return fewShotPlatformService.addExample(code, example);
    }

    @PostMapping("/run")
    public FewShotRunResult run(@RequestBody FewShotRunRequest request) {
        return fewShotPlatformService.run(request.scenarioCode(), request.input());
    }

    @ExceptionHandler(FewShotScenarioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse scenarioNotFound(FewShotScenarioNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record FewShotRunRequest(String scenarioCode, String input) {
    }

    public record ErrorResponse(String message) {
    }
}
