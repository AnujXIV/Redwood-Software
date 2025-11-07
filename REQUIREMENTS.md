# Overview

This project represents a rate-limiter microservice. It's intended to be used by other services
in our application to control access to public facing API end points. 

This rate limiting is done using the "Token Bucket" algorithm.

https://en.wikipedia.org/wiki/Token_bucket

This service is not exposed to the internet, and will only receive traffic from other parts of 
our product. Each end point can have different rate limits, and there is a global rate limit for 
any endpoint without its own limit.

At this time, only a single instance of the rate limiter service is required.

# Configuration

Rate limits are specified in a JSON file that is built into the application. The file is
at `src/main/resources/config.json`. You can load this file as a classpath resource, or from 
the file system directly. Either way your code should work using the command lines listed below.

Rate limits are made up of

- An API endpoint to which the limit will be applied
- The number of requests that are allowed within a given time period.
- The refill rate (specified in tokens/second) that control how fast new token become available

If a request for an unconfigured endpoint comes in, then the global rate limit (denoted as the path `*`) 
is used. This global is shared among all unconfigured endpoints.

An example configuration file is included, and looks like this

```json
{
    "limits": [
        {
            "endpoint": "*",
            "refill-rate": 10,
            "bucket-size": 10
        },
        {
            "endpoint": "/api/v1/users",
            "refill-rate": 1,
            "bucket-size": 3
        },
        {
            "endpoint": "/api/v2/logs",
            "refill-rate": 2,
            "bucket-size": 6
        }
    ]
}
```

# Requirements

- Add in the token bucket algorithm, using any Java classes you see as needed.  
- Connect the token bucket rate limiter to the service's API in `ApiController.java`
- Add appropriate unit tests for the classes you've added. You don't need unit tests for the framework classes.
- Document what've done, and anything else you feel is needed, in a toplevel `README.md` file.

# Building and Running

You will need the following installed

- Java 17
- Gradle build system (> version 8)

## Run

The project can be build and run from the command line using the `gradle bootRun` command (or `./gradlew bootRun`). 

## Test

Unit tests can be executed with `gradle test` (or `./gradlew test`).

## Package

You can create a zip of your source code using `gradle srcZip` (or `./gradlew srcZip`).

## Browser testing

You can access to exposed API endpoint(s) at 

http://localhost:8080/api/

Callers to the API will provide the path to be checked as parameters. For example

http://localhost:8080/api/check?path=/api/v2/logs
