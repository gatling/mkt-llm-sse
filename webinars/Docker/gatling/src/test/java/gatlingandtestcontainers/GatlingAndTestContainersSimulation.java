package gatlingandtestcontainers;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.forAll;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.stressPeakUsers;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;


import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;


public class GatlingAndTestContainersSimulation extends Simulation {
    FeederBuilder<String> feeder = csv("search.csv").random();

    HttpProtocolBuilder httpProtocol =
    http.baseUrl("https://goal-conservation-when-mediterranean.trycloudflare.com")
        .acceptHeader("application/json;q=0.9,text/plain")
        .userAgentHeader(
            "Gatling and Docker"
        );


    ChainBuilder AddProduct = exec(
        feed(feeder),
        http("Add product")
        .post("/api/products")
        .header("Content-Type", "application/json")
        .body(StringBody("{ \"code\": \"#{code}\",\"name\": \"#{name}\",\"description\": \"#{description}\",\"price\": \"#{price}\" }"))
    );

    ChainBuilder FindProduct = exec(
        http("Find Product")
        .get("/api/products/#{code}")
        .header("Content-Type", "application/json")
        .check(status().is(200),jsonPath("$.available").ofBoolean().is(true)
)
    );




    ScenarioBuilder users = scenario("Users").exec(AddProduct,FindProduct);

    {
       setUp(
  users.injectOpen(
    nothingFor(4), // 1
    atOnceUsers(10), // 2
    rampUsers(10).during(5), // 3
    constantUsersPerSec(20).during(15), // 4
    constantUsersPerSec(20).during(15).randomized(), // 5
    rampUsersPerSec(10).to(20).during(10), // 6
    rampUsersPerSec(10).to(20).during(10).randomized(), // 7
    stressPeakUsers(1000).during(20) // 8
  ).protocols(httpProtocol)
);
    }
}
