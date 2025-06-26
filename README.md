# Java Microservice Todo App with CI/CD pipeline using AWS Services


1. Navigate to the EC2 console in the AWS Management Console

2. In the left navigation pane, under 'Load Balancing', click on 'Load Balancers'

3. Select the load balancer named 'todo-alb'

4. In the 'Listeners' tab, find the listener with the ARN 'arn:aws:elasticloadbalancing:eu-west-1:288761743948:listener/app/todo-alb/fd40c26d52b798c1/0e21a923ca0cc70e'

5. Click on 'View/edit rules' for this listener

6. In the 'Add Rule' section, add at least one condition for the rule:
   - Click on 'Add condition'
   - Choose a condition type (e.g., 'Path pattern', 'Host header', 'HTTP header', etc.)
   - Specify the appropriate values for the chosen condition type

7. Add the necessary actions for the rule (e.g., 'Forward to', 'Redirect to', etc.)

8. Click 'Save' to create the rule with the specified condition(s) and action(s)