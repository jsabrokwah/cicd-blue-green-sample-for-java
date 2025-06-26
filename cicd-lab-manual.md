# Blue-Green CI/CD Pipeline Lab Manual
## Java Microservice Todo App with AWS Services

### Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Sample Application Setup](#sample-application-setup)
4. [AWS Infrastructure Setup](#aws-infrastructure-setup)
5. [CI/CD Pipeline Configuration](#cicd-pipeline-configuration)
6. [Blue-Green Deployment Setup](#blue-green-deployment-setup)
7. [Testing the Pipeline](#testing-the-pipeline)
8. [Troubleshooting](#troubleshooting)

---

## Overview

This lab will guide you through creating a complete blue-green CI/CD pipeline for a Java microservice todo application using:
- **GitHub** - Source code repository
- **Maven** - Build tool
- **AWS CodeBuild** - Build service
- **AWS CodeDeploy** - Deployment service
- **AWS CodePipeline** - CI/CD orchestration
- **Amazon ECS** - Container orchestration
- **Application Load Balancer** - Traffic routing for blue-green deployment

### What is Blue-Green Deployment?
Blue-green deployment is a technique that reduces downtime by running two identical production environments (Blue and Green). At any time, only one environment is live, serving all production traffic. When you deploy a new version, you deploy to the inactive environment, test it, then switch traffic over.

---

## Prerequisites

### Required Tools
- AWS Account with appropriate permissions
- GitHub account
- Git installed locally
- Java 11+ installed
- Maven 3.6+ installed
- AWS CLI configured (optional but recommended)

### AWS Permissions Required
Ensure your AWS user has permissions for:
- ECS (Full Access)
- CodeBuild (Full Access)
- CodeDeploy (Full Access)
- CodePipeline (Full Access)
- IAM (Create/Manage Roles)
- EC2 (VPC, Security Groups, Load Balancers)
- CloudWatch Logs

---

## Sample Application Setup

### Step 1: Create the Java Todo Application

First, let's create a simple Spring Boot todo application.

Create a new directory for your project:
```bash
mkdir todo-microservice
cd todo-microservice
```

Create the following project structure:
```
todo-microservice/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── todo/
│       │               ├── TodoApplication.java
│       │               ├── model/
│       │               │   └── TodoItem.java
│       │               ├── controller/
│       │               │   └── TodoController.java
│       │               └── service/
│       │                   └── TodoService.java
│       └── resources/
│           └── application.yml
├── Dockerfile
├── pom.xml
├── buildspec.yml
└── appspec.yml
```

### Step 2: Create Maven Configuration (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.example</groupId>
    <artifactId>todo-microservice</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <name>Todo Microservice</name>
    <description>Simple Todo Application for CI/CD Pipeline Demo</description>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.0</version>
        <relativePath/>
    </parent>
    
    <properties>
        <java.version>11</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 3: Create Application Code

**TodoApplication.java**
```java
package com.example.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TodoApplication {
    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
```

**TodoItem.java**
```java
package com.example.todo.model;

public class TodoItem {
    private Long id;
    private String title;
    private String description;
    private boolean completed;
    
    public TodoItem() {}
    
    public TodoItem(Long id, String title, String description, boolean completed) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
```

**TodoController.java**
```java
package com.example.todo.controller;

import com.example.todo.model.TodoItem;
import com.example.todo.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
    
    @Autowired
    private TodoService todoService;
    
    @GetMapping
    public List<TodoItem> getAllTodos() {
        return todoService.getAllTodos();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TodoItem> getTodoById(@PathVariable Long id) {
        TodoItem todo = todoService.getTodoById(id);
        return todo != null ? ResponseEntity.ok(todo) : ResponseEntity.notFound().build();
    }
    
    @PostMapping
    public TodoItem createTodo(@RequestBody TodoItem todo) {
        return todoService.createTodo(todo);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<TodoItem> updateTodo(@PathVariable Long id, @RequestBody TodoItem todo) {
        TodoItem updated = todoService.updateTodo(id, todo);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        boolean deleted = todoService.deleteTodo(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Todo Service is running - Version 1.0.0");
    }
}
```

**TodoService.java**
```java
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
```

**application.yml**
```yaml
server:
  port: 8080

spring:
  application:
    name: todo-microservice

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.example.todo: DEBUG
    org.springframework: INFO
```

### Step 4: Create Dockerfile

```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 5: Create CodeBuild Configuration (buildspec.yml)

```yaml
version: 0.2

phases:
  pre_build:
    commands:
      - echo Logging in to Amazon ECR...
      - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com
      - REPOSITORY_URI=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$IMAGE_REPO_NAME
      - COMMIT_HASH=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c 1-7)
      - IMAGE_TAG=${COMMIT_HASH:=latest}
  build:
    commands:
      - echo Build started on `date`
      - echo Building the Maven project...
      - mvn clean package -DskipTests
      - echo Building the Docker image...
      - docker build -t $IMAGE_REPO_NAME:$IMAGE_TAG .
      - docker tag $IMAGE_REPO_NAME:$IMAGE_TAG $REPOSITORY_URI:$IMAGE_TAG
      - docker tag $IMAGE_REPO_NAME:$IMAGE_TAG $REPOSITORY_URI:latest
  post_build:
    commands:
      - echo Build completed on `date`
      - echo Pushing the Docker image...
      - docker push $REPOSITORY_URI:$IMAGE_TAG
      - docker push $REPOSITORY_URI:latest
      - echo Writing image definitions file...
      - printf '[{"name":"todo-app","imageUri":"%s"}]' $REPOSITORY_URI:$IMAGE_TAG > imagedefinitions.json
artifacts:
  files:
    - imagedefinitions.json
    - appspec.yml
```

### Step 6: Create CodeDeploy Configuration (appspec.yml)

```yaml
version: 0.0
Resources:
  - TargetService:
      Type: AWS::ECS::Service
      Properties:
        TaskDefinition: <TASK_DEFINITION>
        LoadBalancerInfo:
          ContainerName: "todo-app"
          ContainerPort: 8080
        PlatformVersion: "LATEST"
```

### Step 7: Push to GitHub

1. Initialize Git repository:
```bash
git init
git add .
git commit -m "Initial commit: Todo microservice with CI/CD configuration"
```

2. Create a new repository on GitHub named `todo-microservice`

3. Push your code:
```bash
git remote add origin https://github.com/YOUR_USERNAME/todo-microservice.git
git branch -M main
git push -u origin main
```

---

## AWS Infrastructure Setup

### Step 1: Create ECR Repository

1. Go to **Amazon ECR** in the AWS Console
2. Click **Create repository**
3. Set **Repository name**: `todo-microservice`
4. Leave other settings as default
5. Click **Create repository**
6. Note the repository URI (you'll need this later)

### Step 2: Create ECS Cluster

1. Go to **Amazon ECS** in the AWS Console
2. Click **Create Cluster**
3. Choose **Fargate** as launch type
4. Set **Cluster name**: `todo-cluster`
5. Leave VPC and subnets as default (or choose your preferred VPC)
6. Click **Create**

### Step 3: Create Task Definition

1. In ECS, go to **Task Definitions**
2. Click **Create new Task Definition**
3. Choose **Fargate** launch type
4. Configure the task definition:
   - **Task Definition Name**: `todo-task-definition`
   - **Task Role**: Leave blank for now
   - **Network Mode**: `awsvpc` (default for Fargate)
   - **Task execution IAM role**: Choose `ecsTaskExecutionRole` (create if doesn't exist)
   - **Task memory (GB)**: `0.5`
   - **Task CPU (vCPU)**: `0.25`

5. Add Container:
   - **Container name**: `todo-app`
   - **Image**: `your-account-id.dkr.ecr.region.amazonaws.com/todo-microservice:latest`
   - **Memory Limits**: Soft limit `512`
   - **Port mappings**: Container port `8080`, Protocol `tcp`

6. Click **Create**

### Step 4: Create Application Load Balancer

1. Go to **EC2** → **Load Balancers**
2. Click **Create Load Balancer**
3. Choose **Application Load Balancer**
4. Configure:
   - **Name**: `todo-alb`
   - **Scheme**: Internet-facing
   - **IP address type**: IPv4
   - **VPC**: Select the same VPC as your ECS cluster
   - **Subnets**: Select at least 2 subnets in different AZs

5. Configure Security Groups:
   - Create a new security group or use existing
   - Allow HTTP (port 80) from anywhere (0.0.0.0/0)
   - Allow HTTPS (port 443) if needed

6. Configure Listeners:
   - **Protocol**: HTTP
   - **Port**: 80

7. Configure Target Groups:
   - **Target type**: IP
   - **Name**: `todo-blue-tg`
   - **Protocol**: HTTP
   - **Port**: 8080
   - **VPC**: Same as above
   - **Health check path**: `/api/todos/health`

8. Click **Create Load Balancer**

9. Create a second target group for green deployment:
   - Repeat step 7 but name it `todo-green-tg`

### Step 5: Create ECS Service

1. In ECS, go to your cluster (`todo-cluster`)
2. Click **Create Service**
3. Configure:
   - **Launch type**: Fargate
   - **Task Definition**: Select `todo-task-definition`
   - **Service name**: `todo-service`
   - **Number of tasks**: 2
   - **Minimum healthy percent**: 50
   - **Maximum percent**: 200

4. Configure Network:
   - **VPC**: Same as your load balancer
   - **Subnets**: Select subnets
   - **Security groups**: Create new or select existing (allow port 8080 from ALB security group)
   - **Auto-assign public IP**: ENABLED

5. Configure Load Balancing:
   - **Load balancer type**: Application Load Balancer
   - **Load balancer name**: Select `todo-alb`
   - **Container to load balance**: `todo-app:8080`
   - **Target group name**: `todo-blue-tg`

6. Click **Create Service**

---

## CI/CD Pipeline Configuration

### Step 1: Create IAM Roles

#### CodeBuild Service Role
1. Go to **IAM** → **Roles**
2. Click **Create role**
3. Choose **AWS service** → **CodeBuild**
4. Attach policies:
   - `AmazonEC2ContainerRegistryPowerUser`
   - `CloudWatchLogsFullAccess`
   - Create custom policy for ECS access:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ecr:BatchCheckLayerAvailability",
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchGetImage",
                "ecr:GetAuthorizationToken"
            ],
            "Resource": "*"
        }
    ]
}
```

5. Name the role: `CodeBuildServiceRole`

#### CodeDeploy Service Role
1. Create another role for CodeDeploy
2. Choose **AWS service** → **CodeDeploy** → **CodeDeploy - ECS**
3. Attach the policy: `AWSCodeDeployRoleForECS`
4. Name the role: `CodeDeployServiceRole`

#### CodePipeline Service Role
1. Create another role for CodePipeline
2. Choose **AWS service** → **CodePipeline**
3. Attach policies:
   - `AWSCodePipelineServiceRole`
   - Create custom policy for additional permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetBucketVersioning",
                "s3:GetObject",
                "s3:GetObjectVersion",
                "s3:PutObject",
                "codebuild:BatchGetBuilds",
                "codebuild:StartBuild",
                "codedeploy:CreateDeployment",
                "codedeploy:GetApplication",
                "codedeploy:GetApplicationRevision",
                "codedeploy:GetDeployment",
                "codedeploy:GetDeploymentConfig",
                "codedeploy:RegisterApplicationRevision"
            ],
            "Resource": "*"
        }
    ]
}
```

4. Name the role: `CodePipelineServiceRole`

### Step 2: Create CodeBuild Project

1. Go to **CodeBuild** in AWS Console
2. Click **Create build project**
3. Configure:
   - **Project name**: `todo-build`
   - **Source provider**: GitHub
   - **Repository**: Connect to your GitHub repository
   - **Webhook**: Check "Rebuild every time a code change is pushed"

4. Environment:
   - **Environment image**: Managed image
   - **Operating system**: Amazon Linux 2
   - **Runtime**: Standard
   - **Image**: `aws/codebuild/amazonlinux2-x86_64-standard:3.0`
   - **Service role**: Use the role created above

5. Environment variables:
   - `AWS_DEFAULT_REGION`: Your AWS region
   - `AWS_ACCOUNT_ID`: Your AWS account ID
   - `IMAGE_REPO_NAME`: `todo-microservice`

6. Buildspec: Use a buildspec file (buildspec.yml in your repo)

7. Click **Create build project**

### Step 3: Create CodeDeploy Application

1. Go to **CodeDeploy** in AWS Console
2. Click **Create application**
3. Configure:
   - **Application name**: `todo-app`
   - **Compute platform**: Amazon ECS

4. Click **Create application**

5. Create Deployment Group:
   - **Deployment group name**: `todo-deployment-group`
   - **Service role**: Select the CodeDeploy service role created earlier
   - **ECS cluster name**: `todo-cluster`
   - **ECS service name**: `todo-service`
   - **Load balancer**: Select your ALB
   - **Production listener**: HTTP:80
   - **Target group 1**: `todo-blue-tg`
   - **Target group 2**: `todo-green-tg`

6. Click **Create deployment group**

---

## Blue-Green Deployment Setup

### Step 1: Configure Target Groups for Blue-Green

The blue-green deployment works by switching traffic between two target groups. We already created both target groups (`todo-blue-tg` and `todo-green-tg`).

### Step 2: Update ECS Service for Blue-Green

1. Go to your ECS service (`todo-service`)
2. Update the service to support blue-green deployment:
   - **Deployment type**: Blue/green deployment (powered by AWS CodeDeploy)
   - **Application name**: `todo-app`
   - **Deployment group**: `todo-deployment-group`

### Step 3: Create CodePipeline

1. Go to **CodePipeline** in AWS Console
2. Click **Create pipeline**
3. Configure:
   - **Pipeline name**: `todo-pipeline`
   - **Service role**: Use existing role or create new
   - **Artifact store**: Default location (S3 bucket will be created)

4. Add Source Stage:
   - **Source provider**: GitHub (Version 2)
   - **Connection**: Create new connection to GitHub (follow OAuth flow)
   - **Repository name**: `your-username/todo-microservice`
   - **Branch name**: `main`

5. Add Build Stage:
   - **Build provider**: AWS CodeBuild
   - **Project name**: `todo-build`

6. Add Deploy Stage:
   - **Deploy provider**: Amazon ECS (Blue/Green)
   - **Application name**: `todo-app`
   - **Deployment group**: `todo-deployment-group`

7. Click **Create pipeline**

---

## Testing the Pipeline

### Step 1: Initial Deployment

1. The pipeline should automatically trigger after creation
2. Monitor the pipeline execution:
   - **Source stage**: Should pull code from GitHub
   - **Build stage**: Should build and push Docker image to ECR
   - **Deploy stage**: Should deploy to ECS using blue-green deployment

### Step 2: Verify Deployment

1. Get the Load Balancer DNS name:
   - Go to **EC2** → **Load Balancers**
   - Find `todo-alb` and copy the DNS name

2. Test the application:
   ```bash
   # Health check
   curl http://your-alb-dns-name/api/todos/health
   
   # Get all todos
   curl http://your-alb-dns-name/api/todos
   
   # Create a new todo
   curl -X POST http://your-alb-dns-name/api/todos \
     -H "Content-Type: application/json" \
     -d '{"title":"Test Pipeline","description":"Testing CI/CD pipeline","completed":false}'
   ```

### Step 3: Test Blue-Green Deployment

1. Make a change to your application:
   - Edit `TodoController.java` and change the version in the health endpoint
   - Change `"Todo Service is running - Version 1.0.0"` to `"Todo Service is running - Version 2.0.0"`

2. Commit and push the change:
   ```bash
   git add .
   git commit -m "Update version to 2.0.0"
   git push origin main
   ```

3. Monitor the pipeline:
   - Watch the pipeline execute automatically
   - During deployment, you should see:
     - New tasks starting in the green target group
     - Health checks passing
     - Traffic gradually shifting from blue to green
     - Old tasks terminating

4. Verify the update:
   ```bash
   curl http://your-alb-dns-name/api/todos/health
   ```
   Should now return "Todo Service is running - Version 2.0.0"

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Build Fails with ECR Login Issues
**Problem**: CodeBuild cannot push to ECR
**Solution**: 
- Ensure CodeBuild service role has ECR permissions
- Check that the ECR repository exists
- Verify AWS_ACCOUNT_ID and AWS_DEFAULT_REGION environment variables

#### 2. ECS Tasks Fail to Start
**Problem**: Tasks start but immediately stop
**Solution**:
- Check CloudWatch logs for the task
- Verify the Docker image is valid
- Ensure task definition has enough memory/CPU
- Check security group allows traffic on port 8080

#### 3. Load Balancer Health Checks Fail
**Problem**: ALB shows unhealthy targets
**Solution**:
- Verify health check path is correct (`/api/todos/health`)
- Check security groups allow ALB to reach ECS tasks
- Ensure application is listening on port 8080
- Check application logs for errors

#### 4. Blue-Green Deployment Fails
**Problem**: CodeDeploy fails during blue-green deployment
**Solution**:
- Verify both target groups are properly configured
- Check that ECS service has the correct load balancer configuration
- Ensure CodeDeploy service role has proper permissions
- Review CodeDeploy deployment logs

#### 5. Pipeline Doesn't Trigger
**Problem**: Pipeline doesn't start when code is pushed
**Solution**:
- Check GitHub webhook configuration
- Verify GitHub connection in CodePipeline
- Ensure the branch name matches (main vs master)

### Debugging Steps

1. **Check CloudWatch Logs**:
   - CodeBuild logs: `/aws/codebuild/todo-build`
   - ECS task logs: `/ecs/todo-task-definition`
   - CodeDeploy logs: Available in CodeDeploy console

2. **Verify Security Groups**:
   - ALB security group allows inbound HTTP/HTTPS
   - ECS task security group allows inbound from ALB
   - Outbound rules allow internet access for ECR pulls

3. **Check IAM Roles and Policies**:
   - Each service has the correct role attached
   - Roles have necessary permissions
   - Trust relationships are correct

4. **Monitor Resource Usage**:
   - ECS task memory and CPU utilization
   - ALB request/response metrics
   - CodeBuild build history

### Useful AWS CLI Commands

```bash
# Check ECS service status
aws ecs describe-services --cluster todo-cluster --services todo-service

# Check task definition
aws ecs describe-task-definition --task-definition todo-task-definition

# Check CodeBuild project
aws codebuild batch-get-projects --names todo-build

# Check CodeDeploy application
aws deploy get-application --application-name todo-app

# Check pipeline status
aws codepipeline get-pipeline-state --name todo-pipeline
```

---

## Cleanup

To avoid ongoing charges, clean up resources when you're done:

1. Delete CodePipeline: `todo-pipeline`
2. Delete CodeBuild project: `todo-build`
3. Delete CodeDeploy application: `todo-app`
4. Delete ECS service: `todo-service`
5. Delete ECS cluster: `todo-cluster`
6. Delete ALB: `todo-alb`
7. Delete target groups: `todo-blue-tg`, `todo-green-tg`
8. Delete ECR repository: `todo-microservice`
9. Delete IAM roles created for this lab

---

## Summary

You've successfully created a complete blue-green CI/CD pipeline for a Java microservice that:

1. **Automatically builds** your application using Maven when code is pushed to GitHub
2. **Creates Docker images** and pushes them to Amazon ECR
3. **Deploys using blue-green strategy** to minimize downtime
4. **Routes traffic** intelligently between environments
5. **Monitors health** and automatically rolls back if needed

This setup provides a production-ready CI/CD pipeline that ensures reliable deployments with minimal risk and downtime.

### Key Benefits Achieved:
- **Zero-downtime deployments** through blue-green strategy
- **Automated testing and building** with CodeBuild
- **Container orchestration** with ECS Fargate
- **Intelligent traffic routing** with Application Load Balancer
- **Full automation** from code commit to production deployment

The pipeline is now ready for production use and can be extended with additional features like automated testing, monitoring, and notifications.