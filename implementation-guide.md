Below is a step-by-step guide to setting up a blue/green deployment for a containerized application on Amazon Elastic Container Service (ECS) using AWS CodeDeploy, CodeBuild, CodePipeline, Git, and GitHub, all through the AWS Management Console. I’ll also include a simple sample application (an NGINX web server) to demonstrate the process. The goal is to make this as straightforward as possible, with clear explanations for each step.

Blue/green deployments allow you to deploy a new version of your application alongside the existing one, test it, and then switch traffic to the new version with minimal downtime. If issues arise, you can roll back to the previous version. This guide uses AWS Fargate for simplicity (no need to manage EC2 instances) and assumes you’re starting from scratch.

---

### Step-by-Step Guide to Blue/Green Deployments on ECS

#### Prerequisites
Before starting, ensure you have:
- An AWS account with administrator or PowerUser access.
- A GitHub account with a repository for the sample application.
- The AWS CLI installed (optional, for some manual steps, but we’ll focus on the AWS Management Console).
- Basic familiarity with Git, Docker, and AWS services.

---

### Step 1: Create a Sample Application
We’ll use a simple NGINX web server as the sample application. This application will display a basic HTML page, and we’ll modify it later to test the blue/green deployment.

1. **Create a GitHub Repository**:
   - Log in to GitHub and create a new repository (e.g., `ecs-blue-green-sample`).
   - Initialize it with a `README.md`.

2. **Set Up the Sample Application**:
   - On your local machine, create a directory for the project:
     ```bash
     mkdir ecs-blue-green-sample
     cd ecs-blue-green-sample
     ```
   - Create a `Dockerfile` to build an NGINX image:
     
     FROM nginx:alpine
     COPY index.html /usr/share/nginx/html/index.html
     EXPOSE 80
     CMD ["nginx", "-g", "daemon off;"]
     
   - Create an `index.html` file for the initial (blue) version:
     ```html
     <!DOCTYPE html>
     <html>
     <head>
         <title>Blue Version</title>
     </head>
     <body style="background-color: blue; color: white; text-align: center;">
         <h1>Welcome to the Blue Version!</h1>
     </body>
     </html>
     ```
   - Create a `buildspec.yml` file for CodeBuild to build and push the Docker image:
     ```yaml
     version: 0.2
     phases:
       pre_build:
         commands:
           - echo Logging in to Amazon ECR...
           - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $ECR_REPOSITORY_URI
       build:
         commands:
           - echo Building the Docker image...
           - docker build -t $ECR_REPOSITORY_URI:latest .
           - docker tag $ECR_REPOSITORY_URI:latest $ECR_REPOSITORY_URI:$CODEBUILD_RESOLVED_SOURCE_VERSION
       post_build:
         commands:
           - echo Pushing the Docker image...
           - docker push $ECR_REPOSITORY_URI:latest
           - docker push $ECR_REPOSITORY_URI:$CODEBUILD_RESOLVED_SOURCE_VERSION
           - printf '{"ImageURI":"%s"}' $ECR_REPOSITORY_URI:$CODEBUILD_RESOLVED_SOURCE_VERSION > imageDetail.json
     artifacts:
       files:
         - imageDetail.json
         - appspec.yaml
         - taskdef.json
     ```
   - Create an `appspec.yaml` file for CodeDeploy to specify the ECS service configuration:
     ```yaml
     version: 0.0
     Resources:
       - TargetService:
           Type: AWS::ECS::Service
           Properties:
             TaskDefinition: "<TASK_DEFINITION>"
             LoadBalancerInfo:
               ContainerName: "sample-app"
               ContainerPort: 80
     ```
   - Create a `taskdef.json` file for the ECS task definition:
     ```json
     {
       "family": "ecs-blue-green-task",
       "networkMode": "awsvpc",
       "containerDefinitions": [
         {
           "name": "sample-app",
           "image": "<IMAGE>",
           "essential": true,
           "portMappings": [
             {
               "containerPort": 80,
               "hostPort": 80
             }
           ]
         }
       ],
       "requiresCompatibilities": ["FARGATE"],
       "cpu": "256",
       "memory": "512",
       "executionRoleArn": "<EXECUTION_ROLE_ARN>",
       "taskRoleArn": "<TASK_ROLE_ARN>"
     }
     ```
     *Note*: `<IMAGE>`, `<EXECUTION_ROLE_ARN>`, and `<TASK_ROLE_ARN>` will be updated later.

3. **Push to GitHub**:
   - Initialize a Git repository, commit the files, and push to your GitHub repository:
     ```bash
     git init
     git add .
     git commit -m "Initial commit with sample NGINX application"
     git remote add origin https://github.com/your-username/ecs-blue-green-sample.git
     git push -u origin main
     ```

**Explanation**: These files set up a simple NGINX web server that displays a blue-themed page. The `buildspec.yml` tells CodeBuild how to build and push the Docker image to Amazon ECR. The `appspec.yaml` and `taskdef.json` are used by CodeDeploy and ECS to manage the deployment. We’ll update placeholders later.

---

### Step 2: Set Up Amazon ECR
Amazon Elastic Container Registry (ECR) will store the Docker image for your application.

1. **Create an ECR Repository**:
   - Go to the AWS Management Console.
   - Navigate to **Elastic Container Registry (ECR)**.
   - Click **Create repository**.
   - Enter a repository name (e.g., `ecs-blue-green-sample`).
   - Click **Create repository**.
   - Note the repository URI (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/ecs-blue-green-sample`).

**Explanation**: ECR is where your Docker images are stored. The repository URI will be used in the build and deployment process.

---

### Step 3: Create an ECS Cluster and Service
Set up an ECS cluster and service using AWS Fargate for serverless container management.

1. **Create a VPC and Subnets** (if not already available):
   - Navigate to **VPC** in the AWS Management Console.
   - Click **Create VPC**.
   - Select **VPC and more**.
   - Name: `ecs-blue-green-vpc`.
   - CIDR block: `10.0.0.0/16`.
   - Number of Availability Zones: 2.
   - Number of public subnets: 2.
   - Number of private subnets: 2.
   - Click **Create VPC**.
   - Note the VPC ID and subnet IDs.

2. **Create a Security Group**:
   - In the VPC console, go to **Security Groups** and click **Create security group**.
   - Name: `ecs-blue-green-sg`.
   - Description: Security group for ECS blue/green deployment.
   - VPC: Select the VPC created above.
   - Add an inbound rule:
     - Type: HTTP
     - Protocol: TCP
     - Port range: 80
     - Source: 0.0.0.0/0 (for public access)
   - Click **Create security group**.

3. **Create an Application Load Balancer (ALB)**:
   - Navigate to **EC2** > **Load Balancers**.
   - Click **Create Load Balancer** > **Application Load Balancer**.
   - Name: `ecs-blue-green-alb`.
   - Scheme: Internet-facing.
   - VPC: Select the VPC created.
   - Mappings: Select at least two public subnets.
   - Security groups: Select the security group created.
   - Listeners: Add a listener for HTTP on port 80.
   - Create two target groups:
     - **Target Group 1**:
       - Name: `blue-tg`.
       - Target type: IP.
       - Port: 80.
       - VPC: Select your VPC.
     - **Target Group 2**:
       - Name: `green-tg`.
       - Target type: IP.
       - Port: 80.
       - VPC: Select your VPC.
   - For the listener, set the default action to forward to `blue-tg`.
   - Click **Create**.

4. **Create an ECS Cluster**:
   - Navigate to **Elastic Container Service (ECS)**.
   - Click **Create cluster**.
   - Select **Networking only** (Fargate).
   - Name: `ecs-blue-green-cluster`.
   - Click **Create**.

5. **Create IAM Roles**:
   - Navigate to **IAM** > **Roles**.
   - **Execution Role**:
     - Click **Create role** > **AWS service** > **Elastic Container Service** > **Elastic Container Service Task**.
     - Attach policy: `AmazonECSTaskExecutionRolePolicy`.
     - Name: `ecsTaskExecutionRole`.
     - Create the role and note the ARN.
   - **Task Role**:
     - Click **Create role** > **AWS service** > **Elastic Container Service** > **Elastic Container Service Task**.
     - No additional policies needed for this demo.
     - Name: `ecsTaskRole`.
     - Create the role and note the ARN.
   - **CodeDeploy Role**:
     - Click **Create role** > **AWS service** > **CodeDeploy** > **CodeDeploy**.
     - Attach policy: `AWSCodeDeployRoleForECS`.
     - Name: `ecsCodeDeployRole`.
     - Create the role and note the ARN.

6. **Update `taskdef.json`**:
   - On your local machine, update the `taskdef.json` with the role ARNs:
     - Replace `<EXECUTION_ROLE_ARN>` with the ARN of `ecsTaskExecutionRole`.
     - Replace `<TASK_ROLE_ARN>` with the ARN of `ecsTaskRole`.
   - Commit and push to GitHub:
     ```bash
     git add taskdef.json
     git commit -m "Updated taskdef.json with role ARNs"
     git push
     ```

7. **Create an ECS Task Definition**:
   - In the ECS console, go to **Task Definitions** > **Create new task definition** > **Create new task definition with JSON**.
   - Copy the contents of `taskdef.json` from your local repository.
   - Replace `<IMAGE>` with `123456789012.dkr.ecr.us-east-1.amazonaws.com/ecs-blue-green-sample:latest` (use your ECR URI).
   - Click **Create**.
   - Note the task definition ARN (e.g., `arn:aws:ecs:us-east-1:123456789012:task-definition/ecs-blue-green-task:1`).

8. **Create an ECS Service**:
   - In the ECS console, go to the `ecs-blue-green-cluster` cluster.
   - Click **Create** under **Services**.
   - Launch type: Fargate.
   - Task definition: Select `ecs-blue-green-task` (latest revision).
   - Service name: `ecs-blue-green-service`.
   - Desired tasks: 1.
   - VPC: Select your VPC.
   - Subnets: Select the private subnets.
   - Security group: Select `ecs-blue-green-sg`.
   - Load balancer: Select `ecs-blue-green-alb`.
   - Container to load balance: Select `sample-app:80`.
   - Target group: Select `blue-tg`.
   - Deployment type: Select **Blue/green deployment (powered by AWS CodeDeploy)**.
   - Service role: Select `ecsCodeDeployRole`.
   - Click **Create**.

**Explanation**: The VPC and subnets provide networking for the ECS service. The ALB distributes traffic to the blue or green target group. The security group allows HTTP traffic. The ECS cluster and service run the containerized application using Fargate. The IAM roles grant necessary permissions for ECS and CodeDeploy. The task definition specifies how the container runs, and the service maintains the desired number of tasks.

---

### Step 4: Set Up CodeDeploy
CodeDeploy manages the blue/green deployment, switching traffic between blue and green environments.

1. **Create a CodeDeploy Application**:
   - Navigate to **CodeDeploy** > **Applications** > **Create application**.
   - Name: `ecs-blue-green-app`.
   - Compute platform: Amazon ECS.
   - Click **Create application**.

2. **Create a Deployment Group**:
   - In the CodeDeploy application, click **Create deployment group**.
   - Name: `ecs-blue-green-dg`.
   - Service role: Select `ecsCodeDeployRole`.
   - Deployment type: Blue/green.
   - Environment configuration:
     - Amazon ECS cluster: `ecs-blue-green-cluster`.
     - Amazon ECS service: `ecs-blue-green-service`.
   - Load balancer: Enable and select `ecs-blue-green-alb`.
   - Production target group: `blue-tg`.
   - Test target group: `green-tg`.
   - Deployment settings: Select `CodeDeployDefault.ECSAllAtOnce` for simplicity.
   - Click **Create deployment group**.

**Explanation**: The CodeDeploy application and deployment group define how the blue/green deployment is managed. The production target group (`blue-tg`) handles live traffic, while the test target group (`green-tg`) is used for the new version during deployment.

---

### Step 5: Set Up CodeBuild
CodeBuild will build the Docker image and push it to ECR.

1. **Create a CodeBuild Project**:
   - Navigate to **CodeBuild** > **Create build project**.
   - Project name: `ecs-blue-green-build`.
   - Source provider: GitHub.
   - Connect to GitHub using OAuth (sign in to GitHub when prompted).
   - Repository: Select your `ecs-blue-green-sample` repository.
   - Branch: `main`.
   - Environment:
     - Environment image: Managed image.
     - Operating system: Amazon Linux 2.
     - Runtime: Standard.
     - Image: `aws/codebuild/amazonlinux2-x86_64-standard:5.0`.
     - Privileged: Enable (required for Docker).
   - Service role: Create a new service role (e.g., `codebuild-ecs-blue-green-build-service-role`).
   - Environment variables:
     - Name: `ECR_REPOSITORY_URI`, Value: Your ECR repository URI (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/ecs-blue-green-sample`).
     - Name: `AWS_DEFAULT_REGION`, Value: Your region (e.g., `us-east-1`).
   - Buildspec: Use `buildspec.yml` from the repository.
   - Artifacts: Leave as default.
   - Click **Create build project**.

2. **Add ECR Permissions to CodeBuild Role**:
   - Go to **IAM** > **Roles** > Find `codebuild-ecs-blue-green-build-service-role`.
   - Attach an inline policy:
     ```json
     {
       "Version": "2012-10-17",
       "Statement": [
         {
           "Effect": "Allow",
           "Action": [
             "ecr:GetAuthorizationToken",
             "ecr:BatchCheckLayerAvailability",
             "ecr:CompleteLayerUpload",
             "ecr:InitiateLayerUpload",
             "ecr:PutImage",
             "ecr:UploadLayerPart"
           ],
           "Resource": "*"
         }
       ]
     }
     ```
   - Save the policy.

**Explanation**: CodeBuild uses the `buildspec.yml` to build the Docker image, tag it with the Git commit hash, and push it to ECR. The environment variables provide the necessary configuration, and the IAM policy grants access to ECR.

---

### Step 6: Set Up CodePipeline
CodePipeline orchestrates the CI/CD process, connecting GitHub, CodeBuild, and CodeDeploy.

1. **Create a CodePipeline**:
   - Navigate to **CodePipeline** > **Create pipeline**.
   - Pipeline name: `ecs-blue-green-pipeline`.
   - Service role: Create a new service role.
   - Artifact store: Default (S3 bucket created by CodePipeline).
   - Click **Next**.
   - Source stage:
     - Source provider: GitHub (Version 2).
     - Connect to GitHub and authorize AWS.
     - Repository name: Select `ecs-blue-green-sample`.
     - Branch name: `main`.
     - Output artifact format: CodePipeline default.
     - Click **Next**.
   - Build stage:
     - Build provider: AWS CodeBuild.
     - Project name: Select `ecs-blue-green-build`.
     - Click **Next**.
   - Deploy stage:
     - Deploy provider: Amazon ECS (Blue/Green).
     - Region: Your region.
     - Application name: `ecs-blue-green-app`.
     - Deployment group: `ecs-blue-green-dg`.
     - Task Definition: `BuildArtifact`, `taskdef.json`.
     - AWS CodeDeploy AppSpec File: `BuildArtifact`, `appspec.yaml`.
     - Input Artifact with Image URI: `BuildArtifact`, Placeholder text: `IMAGE`.
     - Click **Next**.
   - Review and click **Create pipeline**.

**Explanation**: CodePipeline automates the process: it detects changes in the GitHub repository, triggers CodeBuild to build and push the Docker image, and then uses CodeDeploy to perform the blue/green deployment. The pipeline uses artifacts from CodeBuild (`imageDetail.json`, `appspec.yaml`, `taskdef.json`) to update the ECS service.

---

### Step 7: Test the Initial Deployment
1. **Trigger the Pipeline**:
   - The pipeline should automatically start after creation. If not, go to the CodePipeline console and click **Release change**.
   - Monitor the pipeline execution. It will:
     - Pull the source code from GitHub.
     - Build the Docker image and push it to ECR via CodeBuild.
     - Deploy the image to ECS via CodeDeploy, initially to the blue environment.

2. **Access the Application**:
   - Go to **EC2** > **Load Balancers** > Select `ecs-blue-green-alb`.
   - Copy the DNS name (e.g., `ecs-blue-green-alb-1234567890.us-east-1.elb.amazonaws.com`).
   - Open it in a browser. You should see the blue version (“Welcome to the Blue Version!”).

**Explanation**: The pipeline deploys the initial version of the application to the blue environment, and the ALB routes traffic to the `blue-tg` target group.

---

### Step 8: Test Blue/Green Deployment
1. **Modify the Application** (Green Version):
   - On your local machine, update `index.html` to create a green version:
     ```html
     <!DOCTYPE html>
     <html>
     <head>
         <title>Green Version</title>
     </head>
     <body style="background-color: green; color: white; text-align: center;">
         <h1>Welcome to the Green Version!</h1>
     </body>
     </html>
     ```
   - Commit and push to GitHub:
     ```bash
     git add index.html
     git commit -m "Updated to green version"
     git push
     ```

2. **Monitor the Pipeline**:
   - Go to the CodePipeline console. The pipeline will detect the change and start.
   - CodeBuild will build and push the new Docker image.
   - CodeDeploy will deploy the new image to the green environment (using `green-tg`).

3. **Verify the Deployment**:
   - In the CodeDeploy console, go to **Deployments** and select the latest deployment.
   - You’ll see the green tasks being deployed alongside the blue tasks.
   - Once the deployment is in the “Ready” state, click **Reroute traffic** to switch traffic to the green environment.
   - Refresh the ALB DNS name in your browser. You should see the green version (“Welcome to the Green Version!”).

4. **Rollback (Optional)**:
   - If there’s an issue, go to the CodeDeploy console, select the deployment, and click **Stop and rollback deployment**.
   - Traffic will revert to the blue environment.

**Explanation**: CodeDeploy deploys the new version to the green environment while keeping the blue environment live. After validation, traffic is switched to the green environment. If issues occur, you can roll back to the blue environment.

---

### Step 9: Clean Up
To avoid incurring charges, delete the resources:
1. **Delete the CodePipeline**: In the CodePipeline console, select the pipeline and click **Delete**.
2. **Delete the CodeBuild Project**: In the CodeBuild console, delete the project.
3. **Delete the CodeDeploy Application**: In the CodeDeploy console, delete the application and deployment group.
4. **Delete the ECS Service and Cluster**:
   - In the ECS console, set the service’s desired count to 0, then delete the service and cluster.
5. **Delete the ALB and Target Groups**: In the EC2 console, delete the load balancer and target groups.
6. **Delete the ECR Repository**: In the ECR console, delete the repository.
7. **Delete the VPC**: In the VPC console, delete the VPC and associated resources.
8. **Delete IAM Roles**: In the IAM console, delete the created roles.

**Explanation**: Cleaning up ensures you don’t incur unexpected costs. Always verify that all resources are deleted.

---

### Summary
This guide walked you through setting up a blue/green deployment for a simple NGINX application on AWS ECS using the AWS Management Console. You created a GitHub repository with the application code, set up ECR, ECS, CodeDeploy, CodeBuild, and CodePipeline, and tested the deployment by switching from a blue to a green version. The process is automated, and you can extend it by adding more complex applications or validation steps.

For further customization, you can:
- Add automated tests in the `buildspec.yml`.
- Use different traffic-shifting strategies in CodeDeploy (e.g., Canary or Linear).
- Integrate with AWS CloudFormation for infrastructure as code, as shown in some reference materials.[](https://aws.amazon.com/blogs/devops/blue-green-deployments-to-amazon-ecs-using-aws-cloudformation-and-aws-codedeploy/)

If you encounter issues, check the AWS documentation or contact AWS support. Let me know if you need help with specific steps or additional features![](https://docs.aws.amazon.com/codepipeline/latest/userguide/tutorials-ecs-ecr-codedeploy.html)[](https://amlanscloud.com/bluegreenpost/)[](https://www.checkmateq.com/blog/blue-green-deployment)