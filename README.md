# Approval workflow challenge
At Light, we want to implement the best in class invoice approval workflow application.
Every time one of our customers receives an invoice from a vendor, an approval request is sent to one or more employees (approvers).

Our customers will configure each step and define how the workflow looks like for them.

One possible interpretation (out of many!) of a workflow is to look at it as a set of rules.
Each rule can be responsible for sending an approval request to the company's desired employee based on one or more constraints.

The decision making about whom to send the approval request can only be based on:
- the invoice amount
- department the invoice is sent to
- whether the invoice requires manager approval

It could be all of these items, or any subset within them.

**Example of a rule:**

Send an approval request to the marketing team manager if the following constraints are true:

- the invoice is related to Marketing team expenses
- the invoice's amount is between 5000 and 10000 USD

## Challenge requirements
To successfully complete this coding challenge, the candidate should:
- provide the database model to support the workflow configuration and execution (a jpeg of the database schema can be put in the README file).
  - Note that it is NOT required to implement the database layer in the code; everything can be done in memory
- implement an application that is able to simulate the execution of the workflow
- ensure that the application supports two ways of sending approval requests:
  - Slack
  - Email
  - both of these channels should be mocked, i.e. `println("sending approval request via Slack")` is all that is needed
- it should be possible to run the application via CLI, by passing as input fields: invoice amount, department and whether manager approval is required.

Please insert the workflow in fig.1 into the database before the solution is handed off.

![code_exercise_diagram (2)](https://user-images.githubusercontent.com/112865589/191920630-6c4e8f8e-a8d9-42c2-b31e-ab2c881ed297.jpg)

Fig. 1

## Assumptions
While designing and implementing the solution the candidate is free to make the following assumptions:

1. Each company will be able to define **only** one workflow. Each new invoice will go through that workflow.
2. A company should be able to modify their workflow at any point, e.g. when the **current** version of the workflow is in action.
3. Amounts are expressed in USD (no need to introduce the concept of a currency)

Any other assumptions or things that you would do differently in a real world scenario, feel free to put in the README - no need to implement them.

## Provided code
We have provided a basic framework and libraries along with code placeholders to help you get started. However, these are just suggestions - feel free to use any framework and tools that you like.

### How to build & run
```sh
./gradlew clean build
./gradlew run
```

## Submitting your solution
1. Commit all your changes.
2. Run `git bundle create challenge-<your-name>.bundle --all`
3. Send us the generated bundle file
