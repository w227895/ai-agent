package com.ke.deepseektools.controller;

import com.ke.deepseektools.prompt.LlmPrompt;
import com.ke.deepseektools.prompt.LlmPromptFewShot;
import com.ke.deepseektools.prompt.LlmPromptScenario;
import com.ke.deepseektools.prompt.LlmPromptService;
import com.ke.deepseektools.prompt.LlmOutputSchema;
import com.ke.deepseektools.prompt.PageResult;
import com.ke.deepseektools.prompt.PromptDictionaries;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LlmPromptController {

    private final LlmPromptService service;

    public LlmPromptController(LlmPromptService service) {
        this.service = service;
    }

    @GetMapping("/prompts")
    public PageResult<LlmPrompt> listPrompts(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "sceneId", required = false) Long sceneId,
            @RequestParam(name = "active", required = false) Boolean active) {
        return service.list(page, size, keyword, sceneId, active);
    }

    @GetMapping("/prompts/{id}")
    public LlmPrompt getPrompt(@PathVariable("id") long id) {
        return service.get(id);
    }

    @GetMapping("/scenes")
    public PageResult<LlmPromptScenario> listScenes(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return service.listScenes(page, size, keyword);
    }

    @GetMapping("/scenes/active")
    public java.util.List<LlmPromptScenario> listActiveScenes() {
        return service.listActiveScenes();
    }

    @GetMapping("/scenes/{id}")
    public LlmPromptScenario getScene(@PathVariable("id") long id) {
        return service.getScene(id);
    }

    @GetMapping("/dictionaries")
    public PromptDictionaries.DictionaryResult dictionaries() {
        return service.dictionaries();
    }

    @GetMapping("/few-shots")
    public PageResult<LlmPromptFewShot> listFewShots(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "promptId", required = false) Long promptId,
            @RequestParam(name = "active", required = false) Boolean active) {
        return service.listFewShots(page, size, keyword, promptId, active);
    }

    @GetMapping("/few-shots/{id}")
    public LlmPromptFewShot getFewShot(@PathVariable("id") long id) {
        return service.getFewShot(id);
    }

    @GetMapping("/output-schemas")
    public PageResult<LlmOutputSchema> listOutputSchemas(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "sceneId", required = false) Long sceneId,
            @RequestParam(name = "active", required = false) Boolean active) {
        return service.listOutputSchemas(page, size, keyword, sceneId, active);
    }

    @GetMapping("/output-schemas/active")
    public java.util.List<LlmOutputSchema> listActiveOutputSchemas() {
        return service.listActiveOutputSchemas();
    }

    @GetMapping("/output-schemas/{id}")
    public LlmOutputSchema getOutputSchema(@PathVariable("id") long id) {
        return service.getOutputSchema(id);
    }

    @PostMapping("/prompts")
    public LlmPrompt createPrompt(@RequestBody LlmPrompt prompt) {
        return service.save(prompt);
    }

    @PostMapping("/scenes")
    public LlmPromptScenario createScene(@RequestBody LlmPromptScenario scenario) {
        return service.saveScene(scenario);
    }

    @PostMapping("/few-shots")
    public LlmPromptFewShot createFewShot(@RequestBody LlmPromptFewShot fewShot) {
        return service.saveFewShot(fewShot);
    }

    @PostMapping("/output-schemas")
    public LlmOutputSchema createOutputSchema(@RequestBody LlmOutputSchema schema) {
        return service.saveOutputSchema(schema);
    }

    @PutMapping("/prompts/{id}")
    public LlmPrompt updatePrompt(@PathVariable("id") long id, @RequestBody LlmPrompt prompt) {
        return service.save(new LlmPrompt(
                id,
                prompt.sceneId(),
                prompt.outputSchemaId(),
                prompt.promptCode(),
                prompt.codeType(),
                prompt.templateType(),
                prompt.userPrompt(),
                prompt.priority(),
                prompt.active(),
                prompt.createTime(),
                prompt.updateTime(),
                prompt.systemPrompt(),
                prompt.mailType()));
    }

    @PutMapping("/output-schemas/{id}")
    public LlmOutputSchema updateOutputSchema(@PathVariable("id") long id, @RequestBody LlmOutputSchema schema) {
        return service.saveOutputSchema(new LlmOutputSchema(
                id,
                schema.schemaCode(),
                schema.schemaName(),
                schema.sceneId(),
                schema.schemaContent(),
                schema.promptFragment(),
                schema.sampleOutput(),
                schema.description(),
                schema.active(),
                schema.createTime(),
                schema.updateTime()));
    }

    @PutMapping("/scenes/{id}")
    public LlmPromptScenario updateScene(@PathVariable("id") long id, @RequestBody LlmPromptScenario scenario) {
        return service.saveScene(new LlmPromptScenario(
                id,
                scenario.sceneCode(),
                scenario.sceneName(),
                scenario.description(),
                scenario.active(),
                scenario.createTime(),
                scenario.updateTime()));
    }

    @PutMapping("/few-shots/{id}")
    public LlmPromptFewShot updateFewShot(@PathVariable("id") long id, @RequestBody LlmPromptFewShot fewShot) {
        return service.saveFewShot(new LlmPromptFewShot(
                id,
                fewShot.promptId(),
                fewShot.title(),
                fewShot.content(),
                fewShot.sortOrder(),
                fewShot.active(),
                fewShot.createTime(),
                fewShot.updateTime()));
    }

    @PostMapping("/prompts/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPromptStatus(@PathVariable("id") long id, @RequestParam("active") boolean active) {
        service.setActive(id, active);
    }

    @PostMapping("/scenes/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSceneStatus(@PathVariable("id") long id, @RequestParam("active") boolean active) {
        service.setSceneActive(id, active);
    }

    @PostMapping("/few-shots/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setFewShotStatus(@PathVariable("id") long id, @RequestParam("active") boolean active) {
        service.setFewShotActive(id, active);
    }

    @PostMapping("/output-schemas/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOutputSchemaStatus(@PathVariable("id") long id, @RequestParam("active") boolean active) {
        service.setOutputSchemaActive(id, active);
    }

    @DeleteMapping("/prompts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrompt(@PathVariable("id") long id) {
        service.delete(id);
    }

    @DeleteMapping("/scenes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScene(@PathVariable("id") long id) {
        service.deleteScene(id);
    }

    @DeleteMapping("/few-shots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFewShot(@PathVariable("id") long id) {
        service.deleteFewShot(id);
    }

    @DeleteMapping("/output-schemas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOutputSchema(@PathVariable("id") long id) {
        service.deleteOutputSchema(id);
    }

    @PostMapping("/test")
    public LlmPromptService.TestResult test(@RequestBody LlmPromptService.TestRequest request) {
        return service.test(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse serverError(RuntimeException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
