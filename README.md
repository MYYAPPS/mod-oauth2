Parquet Bootstrap
=================

Simple template project for creating a [Parquet](https://github.com/patrickvankann/parquet) application, derived from the [standard Vert.x Gradle template](https://github.com/vert-x/vertx-gradle-template).

Includes:

1. Very basic "Hello World" resource accepting GET requests only
2. Integration test demonstrating how to use the Parquet Test Utils to check headers and response body content
3. A basic Locust.io script to enable testing with [Locust.io](http://locust.io)

Example Resource
----------------
Located at `src/main/java/com/mycompany/myproject/resource/HelloWorld.java`.

Demonstrates

1. Creating a response Entity with links, embedded entities and basic properties
2. Setting `ETag`, `Last-Modified` and `Cache-Control` headers
3. Conditional response based on either `ETag` or `Last-Modified` and `If-Match`, `If-None-Match`, `If-Unmodified-Since`, `If-Modified-Since`.

Example Integration Test
------------------------
Located at `src/test/java/com/mycompany/myproject/test/integration/java/HttpResourceIntegrationTest.java`.

Demonstrates how to make a request, specify request headers and verify response headers and response body content.

Locust Script
-------------
Very basic Locust.io script that could serve as a starting point for creating more sophisticated load tests for your application.

Located in `locustfile.py` at the project root.