package com.ke.deepseektools.controller;

import com.ke.deepseektools.prompt.LlmPrompt;
import com.ke.deepseektools.prompt.LlmPromptService;
import com.ke.deepseektools.prompt.PageResult;
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
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        return service.list(page, size, keyword);
    }

    @GetMapping("/prompts/{id}")
    public LlmPrompt getPrompt(@PathVariable("id") long id) {
        return service.get(id);
    }

    @PostMapping("/prompts")
    public LlmPrompt createPrompt(@RequestBody LlmPrompt prompt) {
        return service.save(prompt);
    }

    @PutMapping("/prompts/{id}")
    public LlmPrompt updatePrompt(@PathVariable("id") long id, @RequestBody LlmPrompt prompt) {
        return service.save(new LlmPrompt(
                id,
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

    @PostMapping("/prompts/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPromptStatus(@PathVariable("id") long id, @RequestParam("active") boolean active) {
        service.setActive(id, active);
    }

    @DeleteMapping("/prompts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrompt(@PathVariable("id") long id) {
        service.delete(id);
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
