package Hello;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import net.datafaker.Faker;
import java.util.stream.Stream;
import java.util.function.Supplier;
import java.util.Map;
import java.util.Iterator;

public class HelloSimulation extends Simulation {

    Faker Users = new Faker();

    Iterator<Map<String, Object>> feeder =
            Stream.generate((Supplier<Map<String, Object>>) () -> {
                String firstName = Users.name().firstName();
                String lastName = Users.name().lastName();
                return Map.of(
                        "firstname", firstName,
                        "lastname", lastName
                );
            }).iterator();


    ScenarioBuilder hello_user = scenario("data").exec(
        feed(feeder),
        http("Hello")
            .post("/hello")
                .body(StringBody("{\"firstname\": \"#{firstname}\", \"lastname\": \"#{lastname}\"}"))
                .asJson()
                .check(
                        status().is(201),
                        jsonPath("$.message").is(session -> "Hello "
                                + session.getString("firstname")
                                + " "
                                + session.getString("lastname"))
                )


    );


    HttpProtocolBuilder httpProtocol =
        http.baseUrl("http://127.0.0.1:5000")
            .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

    {
        setUp(
                hello_user.injectOpen(atOnceUsers(10))
        ).protocols(httpProtocol);
    }
}
