package com.example.todo.service;

import com.example.todo.model.TodoItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TodoService {
    
    private final List<TodoItem> todos = new ArrayList<>();
    private final AtomicLong counter = new AtomicLong();
    
    public TodoService() {
        // Add some sample data
        todos.add(new TodoItem(counter.incrementAndGet(), "Learn Spring Boot", "Complete Spring Boot tutorial", false));
        todos.add(new TodoItem(counter.incrementAndGet(), "Setup CI/CD", "Configure AWS CodePipeline", false));
        todos.add(new TodoItem(counter.incrementAndGet(), "Deploy to ECS", "Deploy application to Amazon ECS", false));
    }
    
    public List<TodoItem> getAllTodos() {
        return new ArrayList<>(todos);
    }
    
    public TodoItem getTodoById(Long id) {
        return todos.stream()
                .filter(todo -> todo.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    public TodoItem createTodo(TodoItem todo) {
        todo.setId(counter.incrementAndGet());
        todos.add(todo);
        return todo;
    }
    
    public TodoItem updateTodo(Long id, TodoItem updatedTodo) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).getId().equals(id)) {
                updatedTodo.setId(id);
                todos.set(i, updatedTodo);
                return updatedTodo;
            }
        }
        return null;
    }
    
    public boolean deleteTodo(Long id) {
        return todos.removeIf(todo -> todo.getId().equals(id));
    }
}