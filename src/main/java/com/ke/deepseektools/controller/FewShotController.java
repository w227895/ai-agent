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
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return fewShotPlatformService.listScenariosPage(page, size, keyword);
    }

    @GetMapping("/scenarios/{code}")
    public FewShotScenario getScenario(@PathVariable("code") String code) {
        return fewShotPlatformService.getScenario(code);
    }

    @GetMapping("/scenarios/{code}/prompt-preview")
    public PromptPreview previewPrompt(@PathVariable("code") String code) {
        return fewShotPlatformService.previewPrompt(code, "");
    }

    @GetMapping("/scenarios/{code}/prompts")
    public List<LlmPromptTemplate> listScenarioPrompts(@PathVariable("code") String code) {
        return fewShotPlatformService.listPromptTemplatesByScenario(code);
    }

    @PostMapping("/prompt-preview")
    public PromptPreview previewPrompt(@RequestBody PromptPreviewRequest request) {
        return fewShotPlatformService.previewPrompt(request.scenario(), request.input());
    }

    @PostMapping("/scenarios")
    public FewShotScenario saveScenario(@RequestBody FewShotScenario scenario) {
        return fewShotPlatformService.saveScenario(scenario);
    }

    @PostMapping("/scenarios/{code}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setScenarioStatus(@PathVariable("code") String code, @RequestParam("active") boolean active) {
        fewShotPlatformService.setScenarioActive(code, active);
    }

    @DeleteMapping("/scenarios/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScenario(@PathVariable("code") String code) {
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

    @PostMapping("/prompts/{promptCode}/copy")
    public LlmPromptTemplate copyPromptTemplate(@PathVariable("promptCode") String promptCode) {
        return fewShotPlatformService.copyPromptTemplate(promptCode);
    }

    @PostMapping("/prompts/{promptCode}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPromptStatus(@PathVariable("promptCode") String promptCode, @RequestParam("active") boolean active) {
        fewShotPlatformService.setPromptActive(promptCode, active);
    }

    @DeleteMapping("/prompts/{promptCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePromptTemplate(@PathVariable("promptCode") String promptCode) {
        fewShotPlatformService.deletePromptTemplate(promptCode);
    }

    @GetMapping("/prompts/{promptCode}/output-schemas")
    public List<LlmOutputSchema> listPromptOutputSchemas(@PathVariable("promptCode") String promptCode) {
        return fewShotPlatformService.listOutputSchemasByPrompt(promptCode);
    }

    @GetMapping("/output-schemas")
    public List<LlmOutputSchema> listOutputSchemas() {
        return fewShotPlatformService.listOutputSchemas();
    }

    @PostMapping("/output-schemas")
    public LlmOutputSchema saveOutputSchema(@RequestBody LlmOutputSchema outputSchema) {
        return fewShotPlatformService.saveOutputSchema(outputSchema);
    }

    @PostMapping("/output-schemas/{schemaCode}/copy")
    public LlmOutputSchema copyOutputSchema(@PathVariable("schemaCode") String schemaCode) {
        return fewShotPlatformService.copyOutputSchema(schemaCode);
    }

    @PostMapping("/output-schemas/{schemaCode}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOutputSchemaStatus(@PathVariable("schemaCode") String schemaCode, @RequestParam("active") boolean active) {
        fewShotPlatformService.setOutputSchemaActive(schemaCode, active);
    }

    @DeleteMapping("/output-schemas/{schemaCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOutputSchema(@PathVariable("schemaCode") String schemaCode) {
        fewShotPlatformService.deleteOutputSchema(schemaCode);
    }

    @PostMapping("/scenarios/{code}/examples")
    public FewShotScenario addExample(@PathVariable("code") String code, @RequestBody FewShotExample example) {
        return fewShotPlatformService.addExample(code, example);
    }

    @GetMapping("/scenarios/{code}/failure-cases")
    public List<FewShotFailureCase> listFailureCases(@PathVariable("code") String code) {
        return fewShotPlatformService.listFailureCases(code);
    }

    @PostMapping("/scenarios/{code}/failure-cases/import")
    public List<FewShotFailureCase> importFailureCases(
            @PathVariable("code") String code,
            @RequestBody FailureCaseImportRequest request) {
        return fewShotPlatformService.importFailureCases(code, request.cases());
    }

    @PostMapping("/scenarios/{code}/failure-cases/import-text")
    public List<FewShotFailureCase> importFailureCasesFromText(
            @PathVariable("code") String code,
            @RequestBody FailureCaseTextImportRequest request) {
        return fewShotPlatformService.importFailureCasesFromText(code, request.text());
    }

    @PostMapping("/scenarios/{code}/optimize-prompt")
    public PromptOptimizationResult optimizePrompt(@PathVariable("code") String code) {
        return fewShotPlatformService.optimizePrompt(code);
    }

    @PostMapping("/run")
    public FewShotRunResult run(@RequestBody FewShotRunRequest request) {
        return fewShotPlatformService.run(request.scenarioCode(), request.input(), request.promptCode(), request.schemaCode());
    }

    @GetMapping("/run-logs")
    public FewShotPlatformService.PageResult<com.ke.deepseektools.fewshot.PromptTestRecord> listRunLogs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return fewShotPlatformService.listRunLogsPage(page, size, keyword);
    }

    @GetMapping("/run-logs/{id}")
    public com.ke.deepseektools.fewshot.PromptTestRecord getRunLog(@PathVariable("id") long id) {
        return fewShotPlatformService.getRunLog(id);
    }

    @DeleteMapping("/run-logs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRunLog(@PathVariable("id") long id) {
        fewShotPlatformService.deleteRunLog(id);
    }

    @GetMapping(value = "/run-logs/export", produces = "text/csv;charset=UTF-8")
    public String exportRunLogs(@RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return fewShotPlatformService.exportRunLogsCsv(keyword);
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

    public record FewShotRunRequest(String scenarioCode, String promptCode, String schemaCode, String input) {
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
