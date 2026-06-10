package com.ke.deepseektools.controller;

import java.util.List;

import com.ke.deepseektools.fewshot.FewShotExample;
import com.ke.deepseektools.fewshot.FewShotFailureCase;
import com.ke.deepseektools.fewshot.FewShotPlatformService;
import com.ke.deepseektools.fewshot.FewShotPlatformService.FewShotRunResult;
import com.ke.deepseektools.fewshot.FewShotPlatformService.PromptPreview;
import com.ke.deepseektools.fewshot.FewShotScenario;
import com.ke.deepseektools.fewshot.FewShotScenarioRepository.FewShotScenarioNotFoundException;
import com.ke.deepseektools.fewshot.LlmOutputSchema;
import com.ke.deepseektools.fewshot.LlmPromptTemplate;
import com.ke.deepseektools.fewshot.PromptOptimizationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/scenarios/page")
    public FewShotPlatformService.PageResult<FewShotScenario> listScenariosPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return fewShotPlatformService.listScenariosPage(page, size, keyword);
    }

    @GetMapping("/scenarios/{code}")
    public FewShotScenario getScenario(@PathVariable String code) {
        return fewShotPlatformService.getScenario(code);
    }

    @GetMapping("/scenarios/{code}/prompt-preview")
    public PromptPreview previewPrompt(@PathVariable String code) {
        return fewShotPlatformService.previewPrompt(code, "");
    }

    @PostMapping("/prompt-preview")
    public PromptPreview previewPrompt(@RequestBody PromptPreviewRequest request) {
        return fewShotPlatformService.previewPrompt(request.scenario(), request.input());
    }

    @PostMapping("/scenarios")
    public FewShotScenario saveScenario(@RequestBody FewShotScenario scenario) {
        return fewShotPlatformService.saveScenario(scenario);
    }

    @DeleteMapping("/scenarios/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@PathVariable String code) {
        fewShotPlatformService.deleteScenario(code);
    }

    @GetMapping("/prompts")
    public List<LlmPromptTemplate> listPromptTemplates() {
        return fewShotPlatformService.listPromptTemplates();
    }

    @PostMapping("/prompts")
    public LlmPromptTemplate savePromptTemplate(@RequestBody LlmPromptTemplate promptTemplate) {
        return fewShotPlatformService.savePromptTemplate(promptTemplate);
    }

    @GetMapping("/output-schemas")
    public List<LlmOutputSchema> listOutputSchemas() {
        return fewShotPlatformService.listOutputSchemas();
    }

    @PostMapping("/output-schemas")
    public LlmOutputSchema saveOutputSchema(@RequestBody LlmOutputSchema outputSchema) {
        return fewShotPlatformService.saveOutputSchema(outputSchema);
    }

    @PostMapping("/scenarios/{code}/examples")
    public FewShotScenario addExample(@PathVariable String code, @RequestBody FewShotExample example) {
        return fewShotPlatformService.addExample(code, example);
    }

    @GetMapping("/scenarios/{code}/failure-cases")
    public List<FewShotFailureCase> listFailureCases(@PathVariable String code) {
        return fewShotPlatformService.listFailureCases(code);
    }

    @PostMapping("/scenarios/{code}/failure-cases/import")
    public List<FewShotFailureCase> importFailureCases(
            @PathVariable String code,
            @RequestBody FailureCaseImportRequest request) {
        return fewShotPlatformService.importFailureCases(code, request.cases());
    }

    @PostMapping("/scenarios/{code}/failure-cases/import-text")
    public List<FewShotFailureCase> importFailureCasesFromText(
            @PathVariable String code,
            @RequestBody FailureCaseTextImportRequest request) {
        return fewShotPlatformService.importFailureCasesFromText(code, request.text());
    }

    @PostMapping("/scenarios/{code}/optimize-prompt")
    public PromptOptimizationResult optimizePrompt(@PathVariable String code) {
        return fewShotPlatformService.optimizePrompt(code);
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record FewShotRunRequest(String scenarioCode, String input) {
    }

    public record PromptPreviewRequest(FewShotScenario scenario, String input) {
    }

    public record FailureCaseImportRequest(List<FewShotFailureCase> cases) {
    }

    public record FailureCaseTextImportRequest(String text) {
    }

    public record ErrorResponse(String message) {
    }
}
